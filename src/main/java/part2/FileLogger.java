package part2;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;

public class FileLogger {

    private static final Path LOG_DIR = Path.of("D:", "AgenticAI_Git", "logs");
    private static final Path LOG_FILE = LOG_DIR.resolve("part2-agent.log");

    public static synchronized void log(String level, String message) {
        try {
            Files.createDirectories(LOG_DIR);
            String line = "[" + LocalDateTime.now() + "] " + level + "  " + message + System.lineSeparator();
            Files.writeString(LOG_FILE, line, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            System.err.println("Failed to write log entry: " + e.getMessage());
        }
    }

    public static void info(String message) {
        log("INFO", message);
    }

    public static void warn(String message) {
        log("WARN", message);
    }

    public static void error(String message, Throwable t) {
        log("ERROR", message + " - " + t);
    }
}
