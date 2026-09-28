package Lab5;

import java.time.Instant;

/**
 * One structured step in the agent's execution trace. Every pipeline stage
 * (guardrail check, generation, evaluation, approval decision) emits one of
 * these - never a bare log line - so a run can be replayed and audited step
 * by step after the fact, independent of whatever the agent printed to
 * stdout at the time.
 */
public record TraceEvent(
        Instant timestamp,
        String step,
        String status,
        String detail
) {
    public static TraceEvent of(String step, String status, String detail) {
        return new TraceEvent(Instant.now(), step, status, detail);
    }

    public String toJsonLine() {
        return "{"
                + "\"timestamp\":\"" + timestamp + "\","
                + "\"step\":\"" + escape(step) + "\","
                + "\"status\":\"" + escape(status) + "\","
                + "\"detail\":\"" + escape(detail) + "\""
                + "}";
    }

    private static String escape(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "");
    }
}
