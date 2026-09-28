package part3;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;

/**
 * Logs part3 activity. General info/warn/error go to part3-rag.log.
 * Every user turn additionally logs a SUCCESS or FAILURE entry (status +
 * token usage) to its own dedicated file, so successes and failures can be
 * reviewed independently of each other and of general diagnostic noise.
 */
public class RagLogger {

    private static final Path LOG_DIR = Path.of("D:", "AgenticAI_Git", "logs", "part3_RAG");
    private static final Path GENERAL_LOG_FILE = LOG_DIR.resolve("part3-rag.log");
    private static final Path SUCCESS_LOG_FILE = LOG_DIR.resolve("part3-rag-success.log");
    private static final Path FAILURE_LOG_FILE = LOG_DIR.resolve("part3-rag-failure.log");

    private static synchronized void append(Path file, String line) {
        try {
            Files.createDirectories(LOG_DIR);
            Files.writeString(file, line + System.lineSeparator(), StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            System.err.println("Failed to write log entry to " + file + ": " + e.getMessage());
        }
    }

    public static void log(String level, String message) {
        append(GENERAL_LOG_FILE, "[" + LocalDateTime.now() + "] " + level + "  " + message);
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

    public static void logSuccess(String query, String answer, TokenUsage usage) {
        String entry = "[" + LocalDateTime.now() + "] SUCCESS" + System.lineSeparator()
                + "Query: " + query + System.lineSeparator()
                + "Answer: " + answer + System.lineSeparator()
                + "Token usage: " + usage + System.lineSeparator()
                + "Cost: $" + String.format("%.6f", usage.getCostUsd()) + System.lineSeparator()
                + "---" + System.lineSeparator();
        append(SUCCESS_LOG_FILE, entry);
        info("Turn succeeded. Token usage: " + usage);
    }

    public static void logFailure(String query, Throwable error, TokenUsage usage) {
        String entry = "[" + LocalDateTime.now() + "] FAILURE" + System.lineSeparator()
                + "Query: " + query + System.lineSeparator()
                + "Error: " + error + System.lineSeparator()
                + "Token usage: " + usage + System.lineSeparator()
                + "Cost: $" + String.format("%.6f", usage.getCostUsd()) + System.lineSeparator()
                + "---" + System.lineSeparator();
        append(FAILURE_LOG_FILE, entry);
        error("Turn failed. Token usage: " + usage, error);
    }
}
