package Lab5;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServiceDiscoveryToolTest {

    private final ServiceDiscoveryTool tool = new ServiceDiscoveryTool();

    @Test
    void findsAServiceClassWithNoExistingTest(@TempDir Path root) throws IOException {
        Path sourceRoot = root.resolve("src/main/java");
        Path testRoot = root.resolve("src/test/java");
        writeClass(sourceRoot, "com.acme.billing", "InvoiceService");

        List<ServiceTarget> found = tool.findUntested(sourceRoot, testRoot);

        assertEquals(1, found.size());
        assertEquals("InvoiceService", found.get(0).className());
        assertEquals("com.acme.billing", found.get(0).packageName());
    }

    @Test
    void skipsAServiceClassThatAlreadyHasATest(@TempDir Path root) throws IOException {
        Path sourceRoot = root.resolve("src/main/java");
        Path testRoot = root.resolve("src/test/java");
        writeClass(sourceRoot, "com.acme.billing", "InvoiceService");
        writeTestStub(testRoot, "com.acme.billing", "InvoiceServiceTest");

        List<ServiceTarget> found = tool.findUntested(sourceRoot, testRoot);

        assertTrue(found.isEmpty());
    }

    @Test
    void ignoresNonServiceClasses(@TempDir Path root) throws IOException {
        Path sourceRoot = root.resolve("src/main/java");
        Path testRoot = root.resolve("src/test/java");
        writeClass(sourceRoot, "com.acme.billing", "InvoiceController");

        List<ServiceTarget> found = tool.findUntested(sourceRoot, testRoot);

        assertTrue(found.isEmpty());
    }

    @Test
    void returnsEmptyWhenSourceRootDoesNotExist(@TempDir Path root) throws IOException {
        Path sourceRoot = root.resolve("does-not-exist");
        Path testRoot = root.resolve("src/test/java");

        assertTrue(tool.findUntested(sourceRoot, testRoot).isEmpty());
    }

    @Test
    void discoverAllPairsAServiceWithItsExistingTestFile(@TempDir Path root) throws IOException {
        Path sourceRoot = root.resolve("src/main/java");
        Path testRoot = root.resolve("src/test/java");
        writeClass(sourceRoot, "com.acme.billing", "InvoiceService");
        writeTestStub(testRoot, "com.acme.billing", "InvoiceServiceTest");

        List<ServiceDiscoveryTool.Discovery> discoveries = tool.discoverAll(sourceRoot, testRoot);

        assertEquals(1, discoveries.size());
        assertTrue(discoveries.get(0).hasExistingTest());
        assertEquals(testRoot.resolve("com/acme/billing/InvoiceServiceTest.java"), discoveries.get(0).existingTestFile());
    }

    @Test
    void discoverAllReportsNoExistingTestFileForAnUntestedService(@TempDir Path root) throws IOException {
        Path sourceRoot = root.resolve("src/main/java");
        Path testRoot = root.resolve("src/test/java");
        writeClass(sourceRoot, "com.acme.billing", "InvoiceService");

        List<ServiceDiscoveryTool.Discovery> discoveries = tool.discoverAll(sourceRoot, testRoot);

        assertEquals(1, discoveries.size());
        assertFalse(discoveries.get(0).hasExistingTest());
    }

    private void writeClass(Path sourceRoot, String pkg, String className) throws IOException {
        Path dir = sourceRoot.resolve(pkg.replace('.', '/'));
        Files.createDirectories(dir);
        Files.writeString(dir.resolve(className + ".java"),
                "package " + pkg + ";\n\npublic class " + className + " {\n}\n");
    }

    private void writeTestStub(Path testRoot, String pkg, String testClassName) throws IOException {
        Path dir = testRoot.resolve(pkg.replace('.', '/'));
        Files.createDirectories(dir);
        Files.writeString(dir.resolve(testClassName + ".java"),
                "package " + pkg + ";\n\npublic class " + testClassName + " {\n}\n");
    }
}
