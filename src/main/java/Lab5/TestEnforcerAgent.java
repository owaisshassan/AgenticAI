package Lab5;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Scanner;

/**
 * The agent: enforces test-case writing on newly created *Service classes
 * AND on services that already have a test but are missing coverage for
 * some public method, and self-corrects a test that fails its real run by
 * analyzing the actual failure and retrying - up to a bounded number of
 * attempts, same discipline as this repo's plan.md unit-test-agent.
 *
 * Pipeline, one pass per discovered service:
 *
 *   discover
 *     no existing test  -> guardrail(input) -> generate            -> ...
 *     has existing test -> detect missing methods
 *                            none missing -> ALREADY_COMPLETE, stop
 *                            some missing -> guardrail(input) -> generateWithMissingMethods -> ...
 *     ... -> guardrail(output) -> human approval -> write/overwrite -> evaluate
 *   evaluate fails -> analyze the real Surefire report -> correct -> guardrail(output)
 *                      -> human approval -> overwrite -> evaluate again
 *                      (repeat up to MAX_CORRECTION_ATTEMPTS, then stop and report)
 *
 * All four mandatory elements are wired in, not bolted on after the fact:
 * a step that fails a guardrail or an approval never reaches evaluation or
 * a write, and every step - pass or fail - is recorded to the trace.
 *
 * Collaborators (generator, approval, evaluator) are injected via the
 * package-visible constructor so the whole pipeline can be exercised in a
 * test with a stub generator and a scripted approval - see
 * TestEnforcerAgentTest - without a live gateway call or a real `mvn test`
 * subprocess for every case.
 */
public class TestEnforcerAgent {

    /** Same bound this repo's plan.md unit-test-agent uses for self-correction: try, don't loop forever. */
    public static final int MAX_CORRECTION_ATTEMPTS = 3;

    public enum Outcome {
        WRITTEN_AND_PASSED, WRITTEN_BUT_EVAL_FAILED,
        MISSING_METHODS_ADDED_AND_PASSED, MISSING_METHODS_ADDED_BUT_EVAL_FAILED,
        ALREADY_COMPLETE, REJECTED_BY_GUARDRAIL, REJECTED_BY_HUMAN,
        ALREADY_EXISTS, GENERATION_FAILED
    }

    public record RunResult(ServiceTarget target, Outcome outcome, String detail, int correctionAttempts) {
    }

    private final Path projectRoot;
    private final Path sourceRoot;
    private final Path testRoot;
    private final ServiceDiscoveryTool discoveryTool;
    private final MissingTestCaseDetector missingTestCaseDetector;
    private final TestGenerator generator;
    private final TestWriterTool writerTool;
    private final Guardrails guardrails;
    private final HumanApproval humanApproval;
    private final TestEvaluator evaluator;
    private final SurefireReportReader surefireReportReader;
    private final AgentTrace trace;

    public TestEnforcerAgent(Path projectRoot, Path sourceRoot, Path testRoot, Path traceFile,
                              double minCoveragePercent, Duration testTimeout,
                              Scanner approvalInput, PrintStream approvalOutput) {
        this(projectRoot, sourceRoot, testRoot, traceFile,
                new TestGeneratorTool(),
                new HumanApproval(approvalInput, approvalOutput),
                new TestEvaluator(new ProcessRunner(), new JacocoCoverageReader(), minCoveragePercent, testTimeout));
    }

    TestEnforcerAgent(Path projectRoot, Path sourceRoot, Path testRoot, Path traceFile,
                       TestGenerator generator, HumanApproval humanApproval, TestEvaluator evaluator) {
        this.projectRoot = projectRoot;
        this.sourceRoot = sourceRoot;
        this.testRoot = testRoot;
        this.discoveryTool = new ServiceDiscoveryTool();
        this.missingTestCaseDetector = new MissingTestCaseDetector();
        this.generator = generator;
        this.writerTool = new TestWriterTool();
        this.guardrails = new Guardrails(testRoot);
        this.humanApproval = humanApproval;
        this.evaluator = evaluator;
        this.surefireReportReader = new SurefireReportReader();
        this.trace = new AgentTrace(traceFile);
    }

    public List<RunResult> run() throws IOException, InterruptedException {
        trace.record("discover", "started", "scanning " + sourceRoot + " for *Service classes");
        List<ServiceDiscoveryTool.Discovery> discoveries = discoveryTool.discoverAll(sourceRoot, testRoot);
        trace.record("discover", "completed", "found " + discoveries.size() + " service(s)");

        List<RunResult> results = new java.util.ArrayList<>();
        for (ServiceDiscoveryTool.Discovery discovery : discoveries) {
            results.add(processOne(discovery));
        }
        return results;
    }

    private RunResult processOne(ServiceDiscoveryTool.Discovery discovery) throws IOException, InterruptedException {
        ServiceTarget target = discovery.target();
        return discovery.hasExistingTest()
                ? processServiceWithExistingTest(target, discovery.existingTestFile())
                : processUntestedService(target);
    }

    private RunResult processUntestedService(ServiceTarget target) throws IOException, InterruptedException {
        String who = target.qualifiedName();

        List<Guardrails.Violation> inputViolations = guardrails.checkInput(target);
        if (!inputViolations.isEmpty()) {
            trace.record("guardrail_input", "rejected", who + ": " + inputViolations);
            return new RunResult(target, Outcome.REJECTED_BY_GUARDRAIL, inputViolations.toString(), 0);
        }
        trace.record("guardrail_input", "passed", who);

        String generated;
        try {
            trace.record("generate", "started", who);
            generated = generator.generate(target);
            trace.record("generate", "completed", who + ": " + generated.length() + " chars");
        } catch (IOException | InterruptedException e) {
            trace.record("generate", "failed", who + ": " + e.getMessage());
            return new RunResult(target, Outcome.GENERATION_FAILED, e.getMessage(), 0);
        }

        RunResult guardrailOrApprovalFailure = checkOutputAndApprove(target, generated, "write new test for " + who);
        if (guardrailOrApprovalFailure != null) {
            return guardrailOrApprovalFailure;
        }

        TestWriterTool.WriteResult writeResult = writerTool.write(testRoot, target, generated);
        trace.record("write", writeResult.written() ? "written" : "skipped",
                who + ": " + writeResult.path() + " (" + writeResult.reason() + ")");
        if (!writeResult.written()) {
            return new RunResult(target, Outcome.ALREADY_EXISTS, writeResult.reason(), 0);
        }

        return evaluateWithCorrections(target, Outcome.WRITTEN_AND_PASSED, Outcome.WRITTEN_BUT_EVAL_FAILED);
    }

    private RunResult processServiceWithExistingTest(ServiceTarget target, Path existingTestFile) throws IOException, InterruptedException {
        String who = target.qualifiedName();
        String existingTestSource = Files.readString(existingTestFile);
        String serviceSource = Files.readString(target.sourceFile());

        List<String> missingMethods = missingTestCaseDetector.findMissingMethods(
                serviceSource, target.className(), existingTestSource);
        trace.record("detect_missing_methods", "completed", who + ": " + missingMethods.size() + " missing -> " + missingMethods);

        if (missingMethods.isEmpty()) {
            return new RunResult(target, Outcome.ALREADY_COMPLETE, "existing test already covers every public method", 0);
        }

        List<Guardrails.Violation> inputViolations = guardrails.checkInput(target);
        if (!inputViolations.isEmpty()) {
            trace.record("guardrail_input", "rejected", who + ": " + inputViolations);
            return new RunResult(target, Outcome.REJECTED_BY_GUARDRAIL, inputViolations.toString(), 0);
        }
        trace.record("guardrail_input", "passed", who);

        String generated;
        try {
            trace.record("generate", "started", who + ": extending for " + missingMethods);
            generated = generator.generateWithMissingMethods(target, existingTestSource, missingMethods);
            trace.record("generate", "completed", who + ": " + generated.length() + " chars");
        } catch (IOException | InterruptedException e) {
            trace.record("generate", "failed", who + ": " + e.getMessage());
            return new RunResult(target, Outcome.GENERATION_FAILED, e.getMessage(), 0);
        }

        RunResult guardrailOrApprovalFailure = checkOutputAndApprove(
                target, generated, "add coverage for " + missingMethods + " to " + who + "'s test");
        if (guardrailOrApprovalFailure != null) {
            return guardrailOrApprovalFailure;
        }

        TestWriterTool.WriteResult writeResult = writerTool.overwrite(testRoot, target, generated);
        trace.record("write", "written", who + ": " + writeResult.path() + " (extended with missing methods)");

        return evaluateWithCorrections(target, Outcome.MISSING_METHODS_ADDED_AND_PASSED, Outcome.MISSING_METHODS_ADDED_BUT_EVAL_FAILED);
    }

    /** Runs guardrail(output) then requests human approval; returns a terminal RunResult if either fails, else null to continue. */
    private RunResult checkOutputAndApprove(ServiceTarget target, String generatedSource, String approvalAction)
            throws IOException, InterruptedException {
        String who = target.qualifiedName();

        Path candidateTestFile = testRoot.resolve(target.packageName().replace('.', '/'))
                .resolve(target.className() + "Test.java");
        List<Guardrails.Violation> outputViolations = guardrails.checkOutput(generatedSource);
        outputViolations.addAll(guardrails.checkWriteTarget(candidateTestFile));

        if (!outputViolations.isEmpty()) {
            trace.record("guardrail_output", "rejected", who + ": " + outputViolations);
            return new RunResult(target, Outcome.REJECTED_BY_GUARDRAIL, outputViolations.toString(), 0);
        }
        trace.record("guardrail_output", "passed", who);

        HumanApproval.Decision decision = humanApproval.requestApproval(
                approvalAction,
                "Would write: " + candidateTestFile + "\n--- preview (first 500 chars) ---\n"
                        + generatedSource.substring(0, Math.min(500, generatedSource.length())));
        trace.record("human_approval", decision.approved() ? "approved" : "rejected", who + ": " + decision.reason());
        if (!decision.approved()) {
            return new RunResult(target, Outcome.REJECTED_BY_HUMAN, decision.reason(), 0);
        }
        return null;
    }

    /**
     * Runs the just-written test; on failure, reads the REAL Surefire report
     * for why, asks the generator for a correction grounded in that failure,
     * re-checks the output guardrail and gets human approval again before
     * overwriting, then re-runs - up to MAX_CORRECTION_ATTEMPTS times.
     */
    private RunResult evaluateWithCorrections(ServiceTarget target, Outcome passOutcome, Outcome failOutcome)
            throws IOException, InterruptedException {
        String who = target.qualifiedName();

        trace.record("evaluate", "started", who);
        TestEvaluator.EvaluationResult result = evaluator.evaluate(projectRoot, target);
        trace.record("evaluate", result.passed() ? "passed" : "failed", who + ": " + summarize(result));

        int attempt = 0;
        while (!result.passed() && attempt < MAX_CORRECTION_ATTEMPTS) {
            attempt++;

            List<SurefireReportReader.FailureDetail> failures = surefireReportReader.readFailures(
                    projectRoot.resolve("target/surefire-reports"), target.qualifiedTestClassName());
            trace.record("analyze_failure", "completed", who + ": attempt " + attempt + ", " + failures.size() + " failing test(s): " + failures);

            if (failures.isEmpty()) {
                // No Surefire report to analyze (e.g. the run timed out before it could write one) -
                // there is nothing concrete to correct against, so stop rather than guess.
                trace.record("correct", "skipped", who + ": no Surefire failure detail available to correct against");
                break;
            }

            Path testFile = testRoot.resolve(target.packageName().replace('.', '/')).resolve(target.className() + "Test.java");
            String currentTestSource = Files.readString(testFile);

            String corrected;
            try {
                trace.record("correct", "started", who + ": attempt " + attempt);
                corrected = generator.correct(target, currentTestSource, failures);
                trace.record("correct", "completed", who + ": attempt " + attempt + ", " + corrected.length() + " chars");
            } catch (IOException | InterruptedException e) {
                trace.record("correct", "failed", who + ": attempt " + attempt + ": " + e.getMessage());
                return new RunResult(target, failOutcome, "correction attempt " + attempt + " failed to generate: " + e.getMessage(), attempt);
            }

            RunResult guardrailOrApprovalFailure = checkOutputAndApprove(
                    target, corrected, "apply correction attempt " + attempt + " to " + who + "'s failing test");
            if (guardrailOrApprovalFailure != null) {
                return new RunResult(target, guardrailOrApprovalFailure.outcome(),
                        "correction attempt " + attempt + ": " + guardrailOrApprovalFailure.detail(), attempt);
            }

            writerTool.overwrite(testRoot, target, corrected);
            trace.record("write", "written", who + ": correction attempt " + attempt + " written to " + testFile);

            trace.record("evaluate", "started", who + " (after correction attempt " + attempt + ")");
            result = evaluator.evaluate(projectRoot, target);
            trace.record("evaluate", result.passed() ? "passed" : "failed",
                    who + ": attempt " + attempt + ": " + summarize(result));
        }

        return result.passed()
                ? new RunResult(target, passOutcome, summarize(result), attempt)
                : new RunResult(target, failOutcome, summarize(result) + " after " + attempt + " correction attempt(s)", attempt);
    }

    private String summarize(TestEvaluator.EvaluationResult result) {
        return "testsPassed=" + result.testsPassed()
                + ", coverage=" + String.format("%.1f", result.coveragePercent()) + "%"
                + " (min " + result.minCoveragePercent() + "%)";
    }

    public AgentTrace trace() {
        return trace;
    }

    public static void main(String[] args) throws Exception {
        Path projectRoot = Path.of(".").toAbsolutePath().normalize();
        Path sourceRoot = projectRoot.resolve("src/main/java/part1");
        Path testRoot = projectRoot.resolve("src/test/java/part1");
        Path traceFile = projectRoot.resolve("Lab5-trace-" + Instant.now().toEpochMilli() + ".ndjson");

        TestEnforcerAgent agent = new TestEnforcerAgent(
                projectRoot, sourceRoot, testRoot, traceFile,
                80.0, Duration.ofSeconds(120), new Scanner(System.in), System.out);

        List<RunResult> results = agent.run();
        for (RunResult result : results) {
            System.out.println(result.target().qualifiedName() + " -> " + result.outcome()
                    + " (" + result.detail() + ", correctionAttempts=" + result.correctionAttempts() + ")");
        }
        System.out.println("Trace written to: " + traceFile);
    }
}
