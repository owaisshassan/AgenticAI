package Lab5;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Runs a command with a hard timeout and captured output - a hung `mvn
 * test` must not hang the evaluation step. Minimal deliberately: this lab
 * needs exactly one bounded subprocess call (running the generated test),
 * not a general process-orchestration framework.
 */
public class ProcessRunner {

    public record Result(int exitCode, String stdout, String stderr, boolean timedOut) {
        public boolean succeeded() {
            return !timedOut && exitCode == 0;
        }
    }

    public Result run(List<String> command, Path workingDirectory, Duration timeout) {
        ProcessBuilder builder = new ProcessBuilder(command).directory(workingDirectory.toFile());

        Process process;
        try {
            process = builder.start();
        } catch (IOException e) {
            return new Result(-1, "", "failed to start process: " + e.getMessage(), false);
        }

        Drain stdoutDrain = new Drain(process.getInputStream());
        Drain stderrDrain = new Drain(process.getErrorStream());
        Thread stdoutThread = new Thread(stdoutDrain, "processrunner-stdout");
        Thread stderrThread = new Thread(stderrDrain, "processrunner-stderr");
        stdoutThread.start();
        stderrThread.start();

        boolean finishedInTime;
        try {
            finishedInTime = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            finishedInTime = false;
        }

        if (!finishedInTime) {
            destroyTree(process);
            joinQuietly(stdoutThread, stderrThread);
            return new Result(-1, stdoutDrain.text(), stderrDrain.text(), true);
        }

        joinQuietly(stdoutThread, stderrThread);
        return new Result(process.exitValue(), stdoutDrain.text(), stderrDrain.text(), false);
    }

    /**
     * Kills the whole process tree, not just the direct child - a plain
     * process.destroy()/destroyForcibly() only signals the immediate
     * process (e.g. cmd.exe), leaving any grandchild it spawned (e.g. the
     * ping.exe cmd shells out to) running and still holding open handles.
     * On Windows in particular that orphaned grandchild can keep a working
     * directory or temp file locked well after this method returns.
     */
    private void destroyTree(Process process) {
        List<ProcessHandle> descendants = process.toHandle().descendants().toList();
        descendants.forEach(ProcessHandle::destroy);
        process.destroy();

        try {
            if (!process.waitFor(2, TimeUnit.SECONDS)) {
                descendants.forEach(ProcessHandle::destroyForcibly);
                process.destroyForcibly();
                process.waitFor(2, TimeUnit.SECONDS);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            descendants.forEach(ProcessHandle::destroyForcibly);
            process.destroyForcibly();
        }
    }

    private void joinQuietly(Thread... threads) {
        for (Thread t : threads) {
            try {
                t.join(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private static final class Drain implements Runnable {
        private final java.io.InputStream input;
        private final StringBuilder buffer = new StringBuilder();

        Drain(java.io.InputStream input) {
            this.input = input;
        }

        @Override
        public void run() {
            try (var reader = new java.io.BufferedReader(new java.io.InputStreamReader(input, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    synchronized (buffer) {
                        buffer.append(line).append('\n');
                    }
                }
            } catch (IOException ignored) {
                // process was destroyed mid-read; whatever we captured stands
            }
        }

        String text() {
            synchronized (buffer) {
                return buffer.toString();
            }
        }
    }
}
