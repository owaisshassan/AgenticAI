package Lab5;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Guardrails element (mandatory #1): checks that must pass before the agent
 * is allowed to act, and checks the generated output must pass before it is
 * allowed to reach the human-approval gate. Mirrors this project's existing
 * discipline (project-guardian's WorkspaceGuard confining writes to one
 * root; the unit-test-agent's "never touch src/main/java" rule) rather than
 * inventing a new safety model - a guardrail here is a hard boundary the
 * agent cannot reason its way past, not a prompt instruction.
 */
public class Guardrails {

    public record Violation(String code, String message) {
    }

    private static final Pattern JAVA_IDENTIFIER = Pattern.compile("^[A-Za-z_$][A-Za-z0-9_$]*$");
    private static final int MAX_TEST_FILE_BYTES = 200_000;

    private final Path allowedTestRoot;

    public Guardrails(Path allowedTestRoot) {
        this.allowedTestRoot = allowedTestRoot.toAbsolutePath().normalize();
    }

    /** Pre-generation: is this even a request the agent is allowed to act on? */
    public List<Violation> checkInput(ServiceTarget target) {
        List<Violation> violations = new ArrayList<>();

        if (target.sourceFile() == null || !target.sourceFile().toString().endsWith(".java")) {
            violations.add(new Violation("not_a_java_file", "target must be a .java source file"));
        }
        if (target.className() == null || !JAVA_IDENTIFIER.matcher(target.className()).matches()) {
            violations.add(new Violation("invalid_class_name", "className is not a valid Java identifier"));
        }
        if (target.sourceFile() != null && target.sourceFile().toString().contains("..")) {
            violations.add(new Violation("path_traversal", "sourceFile path must not contain '..'"));
        }
        return violations;
    }

    /**
     * Pre-write: refuses any write outside the configured test root and any
     * write to a path that isn't itself a test file - the same
     * write-scope confinement this repo's unit-test-agent and
     * project-guardian's WorkspaceGuard both enforce, applied here to
     * whatever path the agent is about to create/overwrite.
     */
    public List<Violation> checkWriteTarget(Path candidateTestFile) {
        List<Violation> violations = new ArrayList<>();
        Path resolved = candidateTestFile.toAbsolutePath().normalize();

        if (!resolved.startsWith(allowedTestRoot)) {
            violations.add(new Violation("write_outside_test_root",
                    "refusing to write outside " + allowedTestRoot + ": " + resolved));
        }
        if (!resolved.getFileName().toString().endsWith("Test.java")
                && !resolved.getFileName().toString().endsWith("Tests.java")) {
            violations.add(new Violation("not_a_test_file",
                    "refusing to write a file that isn't named *Test.java or *Tests.java: " + resolved));
        }
        return violations;
    }

    /** Post-generation: is the generated test itself safe to write and run? */
    public List<Violation> checkOutput(String generatedTestSource) {
        List<Violation> violations = new ArrayList<>();

        if (generatedTestSource == null || generatedTestSource.isBlank()) {
            violations.add(new Violation("empty_output", "generated test source is empty"));
            return violations;
        }
        if (generatedTestSource.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > MAX_TEST_FILE_BYTES) {
            violations.add(new Violation("output_too_large", "generated test exceeds " + MAX_TEST_FILE_BYTES + " bytes"));
        }
        if (!generatedTestSource.contains("@Test") && !generatedTestSource.contains("@ParameterizedTest")) {
            violations.add(new Violation("no_test_annotation", "generated source has no @Test method - it would silently assert nothing"));
        }
        if (containsDangerousCall(generatedTestSource)) {
            violations.add(new Violation("dangerous_call", "generated test calls a process/filesystem-destructive API "
                    + "(Runtime.exec, ProcessBuilder, System.exit, File.delete/deleteOnExit outside a temp dir, "
                    + "or recursive deletion) - tests must not shell out or delete real files"));
        }
        if (!generatedTestSource.contains("assert")) {
            violations.add(new Violation("no_assertions", "generated source contains no assertion call - "
                    + "a test with no assertions always passes and tests nothing"));
        }
        return violations;
    }

    private boolean containsDangerousCall(String source) {
        return source.contains("Runtime.getRuntime().exec")
                || source.contains("new ProcessBuilder")
                || source.contains("System.exit")
                || source.contains("Files.delete(Path.of(\"")
                || source.contains("FileUtils.deleteDirectory");
    }
}
