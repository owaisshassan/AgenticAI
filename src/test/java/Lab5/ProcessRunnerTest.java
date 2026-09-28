package Lab5;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProcessRunnerTest {

    private final ProcessRunner runner = new ProcessRunner();

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }

    @Test
    void capturesStdoutAndExitCodeOfAQuickCommand(@TempDir Path dir) {
        List<String> command = isWindows()
                ? List.of("cmd", "/c", "echo hello")
                : List.of("sh", "-c", "echo hello");

        ProcessRunner.Result result = runner.run(command, dir, Duration.ofSeconds(10));

        assertTrue(result.succeeded());
        assertTrue(result.stdout().contains("hello"));
    }

    @Test
    void reportsNonZeroExitCode(@TempDir Path dir) {
        List<String> command = isWindows()
                ? List.of("cmd", "/c", "exit 3")
                : List.of("sh", "-c", "exit 3");

        ProcessRunner.Result result = runner.run(command, dir, Duration.ofSeconds(10));

        assertFalse(result.succeeded());
        assertEquals(3, result.exitCode());
        assertFalse(result.timedOut());
    }

    @Test
    @Timeout(15)
    void aHangingProcessIsKilledAndReportedAsTimedOutRatherThanBlockingTheCaller(@TempDir Path dir) {
        // Sleeps far longer than the timeout given below - proves run() itself
        // returns promptly (within the @Timeout on this test) rather than
        // blocking on the hung child, per the "bounded process execution" rule.
        List<String> command = isWindows()
                ? List.of("cmd", "/c", "ping -n 30 127.0.0.1 >nul")
                : List.of("sh", "-c", "sleep 30");

        ProcessRunner.Result result = runner.run(command, dir, Duration.ofMillis(500));

        assertTrue(result.timedOut());
        assertFalse(result.succeeded());
    }
}
