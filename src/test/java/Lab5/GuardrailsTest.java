package Lab5;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GuardrailsTest {



    @Test
    void checkInput_rejectsInvalidClassName(@TempDir Path testRoot) {
        Guardrails guardrails = new Guardrails(testRoot);
        ServiceTarget target = new ServiceTarget(Path.of("Foo.java"), "com.acme", "not valid");

        List<Guardrails.Violation> violations = guardrails.checkInput(target);

        assertTrue(violations.stream().anyMatch(v -> v.code().equals("invalid_class_name")));
    }

    @Test
    void checkInput_rejectsPathTraversal(@TempDir Path testRoot) {
        Guardrails guardrails = new Guardrails(testRoot);
        ServiceTarget target = new ServiceTarget(Path.of("../../etc/Foo.java"), "com.acme", "Foo");

        List<Guardrails.Violation> violations = guardrails.checkInput(target);

        assertTrue(violations.stream().anyMatch(v -> v.code().equals("path_traversal")));
    }

    @Test
    void checkInput_passesForAValidTarget(@TempDir Path testRoot) {
        Guardrails guardrails = new Guardrails(testRoot);
        ServiceTarget target = new ServiceTarget(Path.of("Foo.java"), "com.acme", "FooService");

        assertTrue(guardrails.checkInput(target).isEmpty());
    }

    @Test
    void checkWriteTarget_rejectsPathOutsideTestRoot(@TempDir Path testRoot) {
        Guardrails guardrails = new Guardrails(testRoot);
        Path outside = testRoot.getParent().resolve("FooTest.java");

        List<Guardrails.Violation> violations = guardrails.checkWriteTarget(outside);

        assertTrue(violations.stream().anyMatch(v -> v.code().equals("write_outside_test_root")));
    }

    @Test
    void checkWriteTarget_rejectsNonTestFileName(@TempDir Path testRoot) {
        Guardrails guardrails = new Guardrails(testRoot);
        Path notATest = testRoot.resolve("FooService.java");

        List<Guardrails.Violation> violations = guardrails.checkWriteTarget(notATest);

        assertTrue(violations.stream().anyMatch(v -> v.code().equals("not_a_test_file")));
    }

    @Test
    void checkWriteTarget_passesForATestFileInsideTestRoot(@TempDir Path testRoot) {
        Guardrails guardrails = new Guardrails(testRoot);
        Path validTestFile = testRoot.resolve("com/acme/FooServiceTest.java");

        assertTrue(guardrails.checkWriteTarget(validTestFile).isEmpty());
    }

    @Test
    void checkOutput_rejectsEmptyOutput(@TempDir Path testRoot) {
        Guardrails guardrails = new Guardrails(testRoot);
        assertTrue(guardrails.checkOutput("").stream().anyMatch(v -> v.code().equals("empty_output")));
    }

    @Test
    void checkOutput_rejectsMissingTestAnnotation(@TempDir Path testRoot) {
        Guardrails guardrails = new Guardrails(testRoot);
        String noTest = "public class FooTest { public void notATest() { assertTrue(true); } }";

        assertTrue(guardrails.checkOutput(noTest).stream().anyMatch(v -> v.code().equals("no_test_annotation")));
    }

    @Test
    void checkOutput_rejectsMissingAssertions(@TempDir Path testRoot) {
        Guardrails guardrails = new Guardrails(testRoot);
        String noAssertion = "public class FooTest { @Test public void t() { new Foo(); } }";

        assertTrue(guardrails.checkOutput(noAssertion).stream().anyMatch(v -> v.code().equals("no_assertions")));
    }

    @Test
    void checkOutput_rejectsDangerousProcessBuilderCall(@TempDir Path testRoot) {
        Guardrails guardrails = new Guardrails(testRoot);
        String dangerous = "public class FooTest { @Test public void t() { new ProcessBuilder(\"rm\").start(); assertTrue(true); } }";

        assertTrue(guardrails.checkOutput(dangerous).stream().anyMatch(v -> v.code().equals("dangerous_call")));
    }

    @Test
    void checkOutput_rejectsDangerousSystemExitCall(@TempDir Path testRoot) {
        Guardrails guardrails = new Guardrails(testRoot);
        String dangerous = "public class FooTest { @Test public void t() { System.exit(1); assertTrue(true); } }";

        assertTrue(guardrails.checkOutput(dangerous).stream().anyMatch(v -> v.code().equals("dangerous_call")));
    }

    @Test
    void checkOutput_passesForAWellFormedTest(@TempDir Path testRoot) {
        Guardrails guardrails = new Guardrails(testRoot);
        String good = "package com.acme; import org.junit.jupiter.api.Test; "
                + "public class FooServiceTest { @Test void t() { assertTrue(true); } }";

        assertTrue(guardrails.checkOutput(good).isEmpty());
    }


}
