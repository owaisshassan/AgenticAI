package Lab5;

import part1.Agent;
import part1.Message;
import part1.RunnerUtil;

import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Calls the LLM gateway to write, extend, or correct a JUnit 5 test for a
 * service class. A write-shaped capability (it produces new source text)
 * but this tool itself performs no filesystem I/O - TestWriterTool owns
 * the actual write, kept as a separate tool so generation and writing can
 * each be guarded, traced, and tested independently (the same
 * split-read-from-write discipline as project-guardian's
 * validate_class_spec vs. scaffold_class).
 */
public class TestGeneratorTool implements Tool, TestGenerator {

    private static final String GENERATE_SYSTEM_PROMPT =
            "You write JUnit 5 tests in Java. Given a service class's full source, write a complete, "
                    + "compilable test class covering: the happy path for each public method, null/empty/boundary "
                    + "edge cases, and any documented exception behavior. Use AssertJ-style or plain JUnit "
                    + "assertions. Output ONLY the raw Java source of the test class - no markdown fences, no "
                    + "explanation before or after.";

    private static final String EXTEND_SYSTEM_PROMPT =
            "You write JUnit 5 tests in Java. You are given a service's full source, an EXISTING test class for "
                    + "it, and a list of public methods that existing test does not exercise. Return the COMPLETE "
                    + "updated test class: keep every existing test method unchanged, and add new @Test methods "
                    + "covering each missing method's happy path plus null/empty/boundary edge cases. Output ONLY "
                    + "the raw Java source of the full updated test class - no markdown fences, no explanation.";

    private static final String CORRECT_SYSTEM_PROMPT =
            "You fix a failing JUnit 5 test in Java. You are given a service's full source, the CURRENT test "
                    + "class source, and the exact failure(s) from the last real test run (method, exception type, "
                    + "message, stack excerpt). Analyze why it failed and return a CORRECTED, complete test class. "
                    + "You may ONLY change the test file - you have no ability to modify the service under test, "
                    + "so never write a correction that assumes the service's behavior will change; instead fix "
                    + "the test's own expectations, setup, or assertions to match the service's real, documented "
                    + "behavior. Keep every test method that is not implicated in the failure unchanged. Output "
                    + "ONLY the raw Java source of the full corrected test class - no markdown fences, no "
                    + "explanation.";

    @Override
    public String name() {
        return "test_generator";
    }

    @Override
    public boolean readOnly() {
        return true;
    }

    @Override
    public String generate(ServiceTarget target) throws IOException, InterruptedException {
        String sourceCode = Files.readString(target.sourceFile());
        String userPrompt = "Package: " + target.packageName() + "\n"
                + "Class name: " + target.className() + "\n"
                + "Test class must be named " + target.className() + "Test and live in package "
                + target.packageName() + ".\n\n"
                + "Source:\n```java\n" + sourceCode + "\n```";
        return callGateway(GENERATE_SYSTEM_PROMPT, userPrompt);
    }

    @Override
    public String generateWithMissingMethods(ServiceTarget target, String existingTestSource, List<String> missingMethods)
            throws IOException, InterruptedException {
        String sourceCode = Files.readString(target.sourceFile());
        String userPrompt = "Package: " + target.packageName() + "\n"
                + "Class name: " + target.className() + "\n"
                + "Methods missing test coverage: " + String.join(", ", missingMethods) + "\n\n"
                + "Service source:\n```java\n" + sourceCode + "\n```\n\n"
                + "Existing test source:\n```java\n" + existingTestSource + "\n```";
        return callGateway(EXTEND_SYSTEM_PROMPT, userPrompt);
    }

    @Override
    public String correct(ServiceTarget target, String currentTestSource, List<SurefireReportReader.FailureDetail> failures)
            throws IOException, InterruptedException {
        String sourceCode = Files.readString(target.sourceFile());
        String failureSummary = failures.stream()
                .map(f -> "- " + f.testMethod() + " (" + f.kind() + "): " + f.type() + ": " + f.message()
                        + "\n  stack: " + f.stackExcerpt())
                .collect(Collectors.joining("\n"));

        String userPrompt = "Package: " + target.packageName() + "\n"
                + "Class name: " + target.className() + "\n\n"
                + "Service source:\n```java\n" + sourceCode + "\n```\n\n"
                + "Current (failing) test source:\n```java\n" + currentTestSource + "\n```\n\n"
                + "Failures from the last real test run:\n" + failureSummary;
        return callGateway(CORRECT_SYSTEM_PROMPT, userPrompt);
    }

    private String callGateway(String systemPrompt, String userPrompt) throws IOException, InterruptedException {
        List<Message> messages = new ArrayList<>();
        messages.add(new Message("user", userPrompt));

        Agent agent = new Agent("claude-sonnet", 4000, systemPrompt, messages);
        String response = RunnerUtil.callGateway(agent);
        String rawText = RunnerUtil.extractAssistantText(response);
        return stripMarkdownFence(rawText);
    }

    private String stripMarkdownFence(String text) {
        String trimmed = text.strip();
        if (trimmed.startsWith("```")) {
            int firstNewline = trimmed.indexOf('\n');
            int lastFence = trimmed.lastIndexOf("```");
            if (firstNewline > 0 && lastFence > firstNewline) {
                trimmed = trimmed.substring(firstNewline + 1, lastFence).strip();
            }
        }
        return trimmed;
    }
}
