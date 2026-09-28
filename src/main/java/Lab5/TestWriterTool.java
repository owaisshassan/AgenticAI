package Lab5;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Write tool: puts generated test source on disk. {@link #write} never
 * overwrites an existing file - same refuse-to-overwrite discipline as
 * project-guardian's scaffold_class. {@link #overwrite} is the deliberate
 * exception used only by the extend-missing-coverage and correct-a-failure
 * flows, which are updating a test THIS agent already wrote (or is about
 * to, with human approval) - never a service's source, and never a test
 * file this agent has no record of having produced.
 */
public class TestWriterTool implements Tool {

    @Override
    public String name() {
        return "test_writer";
    }

    @Override
    public boolean readOnly() {
        return false;
    }

    public record WriteResult(boolean written, Path path, String reason) {
    }

    public WriteResult write(Path testRoot, ServiceTarget target, String testSource) throws IOException {
        Path testFile = testPath(testRoot, target);

        if (Files.exists(testFile)) {
            return new WriteResult(false, testFile, "a test file already exists at this path - refusing to overwrite");
        }

        Files.createDirectories(testFile.getParent());
        Files.writeString(testFile, testSource);
        return new WriteResult(true, testFile, "created");
    }

    /** Replaces an existing test file's content - used only for the extend/correct flows, both human-approved. */
    public WriteResult overwrite(Path testRoot, ServiceTarget target, String testSource) throws IOException {
        Path testFile = testPath(testRoot, target);
        Files.createDirectories(testFile.getParent());
        Files.writeString(testFile, testSource);
        return new WriteResult(true, testFile, "overwritten");
    }

    private Path testPath(Path testRoot, ServiceTarget target) {
        return testRoot.resolve(target.packageName().replace('.', '/'))
                .resolve(target.className() + "Test.java");
    }
}
