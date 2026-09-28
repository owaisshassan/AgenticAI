package com.aira.guardian.proc;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Shared timeout wrapper for every subprocess this server launches
 * (mvn test, mvn package, git diff). Every write/build/git tool routes
 * through this - a hung process must never hang a tool call (see
 * project-guardian's design rules: "Bounded process execution").
 *
 * Tightens two gaps found in this repo's existing subprocess pattern
 * (part1/src/main/java/part4/Clinic.java ThrowawayOps.close(), which calls
 * process.waitFor() with no timeout and could itself hang forever if the
 * child ignores SIGTERM): here waitFor always takes an explicit timeout,
 * and a process that doesn't exit after destroy() is destroyed forcibly.
 */
public class BoundedProcess {

    public record Result(int exitCode, String stdout, String stderr, boolean timedOut) {
        public boolean succeeded() {
            return !timedOut && exitCode == 0;
        }
    }

    public Result run(List<String> command, Path workingDirectory, Duration timeout) {
        ProcessBuilder builder = new ProcessBuilder(command)
                .directory(workingDirectory.toFile())
                .redirectErrorStream(false);

        Process process;
        try {
            process = builder.start();
        } catch (IOException e) {
            return new Result(-1, "", "failed to start process: " + e.getMessage(), false);
        }

        StreamGobbler stdoutGobbler = new StreamGobbler(process.getInputStream());
        StreamGobbler stderrGobbler = new StreamGobbler(process.getErrorStream());
        Thread stdoutThread = new Thread(stdoutGobbler, "guardian-proc-stdout");
        Thread stderrThread = new Thread(stderrGobbler, "guardian-proc-stderr");
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
            destroyForcibly(process);
            joinQuietly(stdoutThread, stderrThread);
            return new Result(-1, stdoutGobbler.text(), stderrGobbler.text(), true);
        }

        joinQuietly(stdoutThread, stderrThread);
        return new Result(process.exitValue(), stdoutGobbler.text(), stderrGobbler.text(), false);
    }

    private void destroyForcibly(Process process) {
        process.destroy();
        try {
            if (!process.waitFor(2, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                process.waitFor(2, TimeUnit.SECONDS);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
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

    private static final class StreamGobbler implements Runnable {
        private final java.io.InputStream input;
        private final StringBuilder buffer = new StringBuilder();

        StreamGobbler(java.io.InputStream input) {
            this.input = input;
        }

        @Override
        public void run() {
            try (var reader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(input, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    synchronized (buffer) {
                        buffer.append(line).append('\n');
                    }
                }
            } catch (IOException ignored) {
                // Process was destroyed mid-read; whatever we captured stands.
            }
        }

        String text() {
            synchronized (buffer) {
                return buffer.toString();
            }
        }
    }
}
