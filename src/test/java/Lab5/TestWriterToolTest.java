package Lab5;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestWriterToolTest {

    private final TestWriterTool writer = new TestWriterTool();

    @Test
    void writesANewTestFile(@TempDir Path testRoot) throws IOException {
        ServiceTarget target = new ServiceTarget(Path.of("Foo.java"), "com.acme", "FooService");

        TestWriterTool.WriteResult result = writer.write(testRoot, target, "public class FooServiceTest {}");

        assertTrue(result.written());
        assertTrue(Files.exists(result.path()));
        assertEquals("com/acme/FooServiceTest.java".replace('/', java.io.File.separatorChar),
                testRoot.relativize(result.path()).toString());
    }

    @Test
    void refusesToOverwriteAnExistingTestFile(@TempDir Path testRoot) throws IOException {
        ServiceTarget target = new ServiceTarget(Path.of("Foo.java"), "com.acme", "FooService");
        writer.write(testRoot, target, "public class FooServiceTest { /* v1 */ }");

        TestWriterTool.WriteResult second = writer.write(testRoot, target, "public class FooServiceTest { /* v2 */ }");

        assertFalse(second.written());
        String content = Files.readString(second.path());
        assertTrue(content.contains("v1"));
    }

    @Test
    void overwriteReplacesAnExistingTestFilesContent(@TempDir Path testRoot) throws IOException {
        ServiceTarget target = new ServiceTarget(Path.of("Foo.java"), "com.acme", "FooService");
        writer.write(testRoot, target, "public class FooServiceTest { /* v1 */ }");

        TestWriterTool.WriteResult result = writer.overwrite(testRoot, target, "public class FooServiceTest { /* v2 */ }");

        assertTrue(result.written());
        String content = Files.readString(result.path());
        assertTrue(content.contains("v2"));
        assertFalse(content.contains("v1"));
    }
}
