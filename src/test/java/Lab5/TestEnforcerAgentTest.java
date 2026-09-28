package Lab5;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Scanner;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Full-pipeline tests with the network (TestGenerator) and the real `mvn
 * test` subprocess (TestEvaluator) both stubbed out - proves the four
 * mandatory elements (guardrails, evaluation, trace, human approval), the
 * missing-method-detection extension, and the analyze-and-correct retry
 * loop are all actually wired together in the right order, without
 * spending real API calls or real build time on every case.
 * GuardrailsTest/AgentTraceTest/HumanApprovalTest/SurefireReportReaderTest/
 * ProcessRunnerTest already cover those real implementations in isolation.
 */
class TestEnforcerAgentTest {

    private static final String VALID_GENERATED_TEST =
            "package demo;\n\nimport org.junit.jupiter.api.Test;\nimport static org.junit.jupiter.api.Assertions.assertEquals;\n\n"
                    + "public class WidgetServiceTest {\n    @Test\n    void t() { assertEquals(1, 1); }\n}\n";

    @Test
    void happyPath_writesTracesAndPassesEvaluationWhenApproved(@TempDir Path root) throws IOException, InterruptedException {
        Path sourceRoot = writeService(root, "demo", "WidgetService");
        Path testRoot = root.resolve("src/test/java");
        Path traceFile = root.resolve("trace.ndjson");

        TestEnforcerAgent agent = new TestEnforcerAgent(
                root, sourceRoot, testRoot, traceFile,
                stubGenerator(VALID_GENERATED_TEST),
                approvalScript("y\n"),
                stubEvaluator(r -> passing(90.0)));

        List<TestEnforcerAgent.RunResult> results = agent.run();

        assertEquals(1, results.size());
        assertEquals(TestEnforcerAgent.Outcome.WRITTEN_AND_PASSED, results.get(0).outcome());
        assertEquals(0, results.get(0).correctionAttempts());
        assertTrue(Files.exists(testRoot.resolve("demo/WidgetServiceTest.java")));

        List<TraceEvent> events = agent.trace().events();
        assertTrue(events.stream().anyMatch(e -> e.step().equals("discover")));
        assertTrue(events.stream().anyMatch(e -> e.step().equals("guardrail_input") && e.status().equals("passed")));
        assertTrue(events.stream().anyMatch(e -> e.step().equals("generate") && e.status().equals("completed")));
        assertTrue(events.stream().anyMatch(e -> e.step().equals("guardrail_output") && e.status().equals("passed")));
        assertTrue(events.stream().anyMatch(e -> e.step().equals("human_approval") && e.status().equals("approved")));
        assertTrue(events.stream().anyMatch(e -> e.step().equals("write") && e.status().equals("written")));
        assertTrue(events.stream().anyMatch(e -> e.step().equals("evaluate") && e.status().equals("passed")));
    }

    @Test
    void humanRefusal_stopsBeforeAnyFileIsWritten(@TempDir Path root) throws IOException, InterruptedException {
        Path sourceRoot = writeService(root, "demo", "WidgetService");
        Path testRoot = root.resolve("src/test/java");
        Path traceFile = root.resolve("trace.ndjson");

        TestEnforcerAgent agent = new TestEnforcerAgent(
                root, sourceRoot, testRoot, traceFile,
                stubGenerator(VALID_GENERATED_TEST),
                approvalScript("n\n"),
                stubEvaluator(r -> passing(90.0)));

        List<TestEnforcerAgent.RunResult> results = agent.run();

        assertEquals(TestEnforcerAgent.Outcome.REJECTED_BY_HUMAN, results.get(0).outcome());
        assertFalse(Files.exists(testRoot.resolve("demo/WidgetServiceTest.java")));
        assertTrue(agent.trace().events().stream().noneMatch(e -> e.step().equals("write")));
        assertTrue(agent.trace().events().stream().noneMatch(e -> e.step().equals("evaluate")));
    }

    @Test
    void outputGuardrailRejection_neverReachesApprovalOrWrite(@TempDir Path root) throws IOException, InterruptedException {
        Path sourceRoot = writeService(root, "demo", "WidgetService");
        Path testRoot = root.resolve("src/test/java");
        Path traceFile = root.resolve("trace.ndjson");

        // No @Test annotation and no assertion - fails the output guardrail.
        TestEnforcerAgent agent = new TestEnforcerAgent(
                root, sourceRoot, testRoot, traceFile,
                stubGenerator("public class WidgetServiceTest { void notATest() {} }"),
                approvalScript("y\n"),
                stubEvaluator(r -> passing(90.0)));

        List<TestEnforcerAgent.RunResult> results = agent.run();

        assertEquals(TestEnforcerAgent.Outcome.REJECTED_BY_GUARDRAIL, results.get(0).outcome());
        assertFalse(Files.exists(testRoot.resolve("demo/WidgetServiceTest.java")));
        assertTrue(agent.trace().events().stream().noneMatch(e -> e.step().equals("human_approval")));
        assertTrue(agent.trace().events().stream().noneMatch(e -> e.step().equals("write")));
    }

    @Test
    void evaluationFailure_exhaustsCorrectionAttemptsAndReportsFailure(@TempDir Path root) throws IOException, InterruptedException {
        Path sourceRoot = writeService(root, "demo", "WidgetService");
        Path testRoot = root.resolve("src/test/java");
        Path traceFile = root.resolve("trace.ndjson");
        Path surefireDir = root.resolve("target/surefire-reports");
        writeFailingSurefireReport(surefireDir, "demo.WidgetServiceTest", "t");

        TestEnforcerAgent agent = new TestEnforcerAgent(
                root, sourceRoot, testRoot, traceFile,
                stubGenerator(VALID_GENERATED_TEST),
                approvalScript("y\ny\ny\ny\n"),
                stubEvaluator(r -> failing()));

        List<TestEnforcerAgent.RunResult> results = agent.run();

        assertEquals(TestEnforcerAgent.Outcome.WRITTEN_BUT_EVAL_FAILED, results.get(0).outcome());
        assertEquals(TestEnforcerAgent.MAX_CORRECTION_ATTEMPTS, results.get(0).correctionAttempts());
        assertTrue(Files.exists(testRoot.resolve("demo/WidgetServiceTest.java")));
    }

    @Test
    void evaluationFailure_thenCorrectionPassesOnFirstAttempt(@TempDir Path root) throws IOException, InterruptedException {
        Path sourceRoot = writeService(root, "demo", "WidgetService");
        Path testRoot = root.resolve("src/test/java");
        Path traceFile = root.resolve("trace.ndjson");
        Path surefireDir = root.resolve("target/surefire-reports");
        writeFailingSurefireReport(surefireDir, "demo.WidgetServiceTest", "t");

        // First evaluate() call (right after the initial write) fails; every
        // subsequent call (after a correction is applied) passes.
        java.util.concurrent.atomic.AtomicInteger evalCalls = new java.util.concurrent.atomic.AtomicInteger(0);

        TestEnforcerAgent agent = new TestEnforcerAgent(
                root, sourceRoot, testRoot, traceFile,
                stubGenerator(VALID_GENERATED_TEST),
                approvalScript("y\ny\n"),
                stubEvaluator(r -> evalCalls.getAndIncrement() == 0 ? failing() : passing(95.0)));

        List<TestEnforcerAgent.RunResult> results = agent.run();

        assertEquals(TestEnforcerAgent.Outcome.WRITTEN_AND_PASSED, results.get(0).outcome());
        assertEquals(1, results.get(0).correctionAttempts());

        List<TraceEvent> events = agent.trace().events();
        assertTrue(events.stream().anyMatch(e -> e.step().equals("analyze_failure")));
        assertTrue(events.stream().anyMatch(e -> e.step().equals("correct") && e.status().equals("completed")));
    }

    @Test
    void serviceWithExistingTestButMissingMethodCoverage_getsExtendedNotOverwrittenFromScratch(@TempDir Path root) throws IOException, InterruptedException {
        Path sourceRoot = root.resolve("src/main/java");
        Path testRoot = root.resolve("src/test/java");
        writeClass(sourceRoot, "demo", "WidgetService",
                "public class WidgetService {\n    public int size() { return 1; }\n    public int weight() { return 2; }\n}\n");
        writeClass(testRoot, "demo", "WidgetServiceTest",
                "public class WidgetServiceTest {\n    @Test void t() { assertEquals(1, new WidgetService().size()); }\n}\n");

        Path traceFile = root.resolve("trace.ndjson");
        String extended = "public class WidgetServiceTest {\n"
                + "    @Test void t() { assertEquals(1, new WidgetService().size()); }\n"
                + "    @Test void weightWorks() { assertEquals(2, new WidgetService().weight()); }\n}\n";

        TestEnforcerAgent agent = new TestEnforcerAgent(
                root, sourceRoot, testRoot, traceFile,
                stubGenerator(extended),
                approvalScript("y\n"),
                stubEvaluator(r -> passing(88.0)));

        List<TestEnforcerAgent.RunResult> results = agent.run();

        assertEquals(TestEnforcerAgent.Outcome.MISSING_METHODS_ADDED_AND_PASSED, results.get(0).outcome());
        String written = Files.readString(testRoot.resolve("demo/WidgetServiceTest.java"));
        assertTrue(written.contains("weightWorks"));
        assertTrue(written.contains("assertEquals(1, new WidgetService().size())"), "must keep the pre-existing test method");
    }

    @Test
    void serviceWithExistingTestCoveringEveryMethod_isReportedAlreadyCompleteWithoutCallingGeneratorOrApproval(@TempDir Path root) throws IOException, InterruptedException {
        Path sourceRoot = root.resolve("src/main/java");
        Path testRoot = root.resolve("src/test/java");
        writeClass(sourceRoot, "demo", "WidgetService", "public class WidgetService {\n    public int size() { return 1; }\n}\n");
        writeClass(testRoot, "demo", "WidgetServiceTest",
                "public class WidgetServiceTest {\n    @Test void t() { assertEquals(1, new WidgetService().size()); }\n}\n");

        Path traceFile = root.resolve("trace.ndjson");

        TestGenerator failIfCalled = new TestGenerator() {
            public String generate(ServiceTarget t) { throw new AssertionError("must not be called"); }
            public String generateWithMissingMethods(ServiceTarget t, String s, List<String> m) { throw new AssertionError("must not be called"); }
            public String correct(ServiceTarget t, String s, List<SurefireReportReader.FailureDetail> f) { throw new AssertionError("must not be called"); }
        };

        TestEnforcerAgent agent = new TestEnforcerAgent(
                root, sourceRoot, testRoot, traceFile,
                failIfCalled, approvalScript(""), stubEvaluator(r -> passing(100.0)));

        List<TestEnforcerAgent.RunResult> results = agent.run();

        assertEquals(TestEnforcerAgent.Outcome.ALREADY_COMPLETE, results.get(0).outcome());
        assertTrue(agent.trace().events().stream().noneMatch(e -> e.step().equals("human_approval")));
    }

    @Test
    void generationFailure_isReportedAndNeverReachesGuardrailOutputOrApproval(@TempDir Path root) throws IOException, InterruptedException {
        Path sourceRoot = writeService(root, "demo", "WidgetService");
        Path testRoot = root.resolve("src/test/java");
        Path traceFile = root.resolve("trace.ndjson");

        TestGenerator alwaysThrows = new TestGenerator() {
            public String generate(ServiceTarget t) throws IOException { throw new IOException("gateway unreachable"); }
            public String generateWithMissingMethods(ServiceTarget t, String s, List<String> m) throws IOException { throw new IOException("gateway unreachable"); }
            public String correct(ServiceTarget t, String s, List<SurefireReportReader.FailureDetail> f) throws IOException { throw new IOException("gateway unreachable"); }
        };

        TestEnforcerAgent agent = new TestEnforcerAgent(
                root, sourceRoot, testRoot, traceFile,
                alwaysThrows, approvalScript("y\n"), stubEvaluator(r -> passing(90.0)));

        List<TestEnforcerAgent.RunResult> results = agent.run();

        assertEquals(TestEnforcerAgent.Outcome.GENERATION_FAILED, results.get(0).outcome());
        assertTrue(agent.trace().events().stream().noneMatch(e -> e.step().equals("guardrail_output")));
        assertTrue(agent.trace().events().stream().noneMatch(e -> e.step().equals("human_approval")));
    }

    private Path writeService(Path root, String pkg, String className) throws IOException {
        Path sourceRoot = root.resolve("src/main/java");
        writeClass(sourceRoot, pkg, className, "package " + pkg + ";\n\npublic class " + className
                + " {\n    public int size() { return 1; }\n}\n");
        return sourceRoot;
    }

    private void writeClass(Path root, String pkg, String className, String body) throws IOException {
        Path dir = root.resolve(pkg.replace('.', '/'));
        Files.createDirectories(dir);
        String source = body.startsWith("package") ? body : "package " + pkg + ";\n\n" + body;
        Files.writeString(dir.resolve(className + ".java"), source);
    }

    private void writeFailingSurefireReport(Path surefireDir, String qualifiedTestClass, String methodName) throws IOException {
        Files.createDirectories(surefireDir);
        String xml = "<?xml version=\"1.0\"?>\n"
                + "<testsuite>\n"
                + "  <testcase name=\"" + methodName + "\" classname=\"" + qualifiedTestClass + "\">\n"
                + "    <failure message=\"expected: &lt;2&gt; but was: &lt;1&gt;\" type=\"org.opentest4j.AssertionFailedError\">"
                + "at " + qualifiedTestClass + "." + methodName + "(Test.java:10)</failure>\n"
                + "  </testcase>\n"
                + "</testsuite>\n";
        Files.writeString(surefireDir.resolve("TEST-" + qualifiedTestClass + ".xml"), xml);
    }

    private TestGenerator stubGenerator(String output) {
        return new TestGenerator() {
            public String generate(ServiceTarget target) {
                return output;
            }

            public String generateWithMissingMethods(ServiceTarget target, String existingTestSource, List<String> missingMethods) {
                return output;
            }

            public String correct(ServiceTarget target, String currentTestSource, List<SurefireReportReader.FailureDetail> failures) {
                return output;
            }
        };
    }

    private HumanApproval approvalScript(String script) {
        return new HumanApproval(new Scanner(script), new PrintStream(java.io.OutputStream.nullOutputStream()));
    }

    private TestEvaluator.EvaluationResult passing(double coveragePercent) {
        return new TestEvaluator.EvaluationResult(true, coveragePercent, 80.0, coveragePercent >= 80.0, "stubbed");
    }

    private TestEvaluator.EvaluationResult failing() {
        return new TestEvaluator.EvaluationResult(false, 0.0, 80.0, false, "stubbed failure");
    }

    private TestEvaluator stubEvaluator(Function<Void, TestEvaluator.EvaluationResult> resultSupplier) {
        return new StubTestEvaluator(resultSupplier);
    }

    /** A TestEvaluator that skips the real `mvn test` subprocess and JaCoCo read entirely. */
    private static final class StubTestEvaluator extends TestEvaluator {
        private final Function<Void, TestEvaluator.EvaluationResult> resultSupplier;

        StubTestEvaluator(Function<Void, TestEvaluator.EvaluationResult> resultSupplier) {
            super(new ProcessRunner(), new JacocoCoverageReader(), 80.0, Duration.ofSeconds(1));
            this.resultSupplier = resultSupplier;
        }

        @Override
        public EvaluationResult evaluate(Path projectRoot, ServiceTarget target) {
            return resultSupplier.apply(null);
        }
    }
}
