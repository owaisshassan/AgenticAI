package Lab5;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Trace element (mandatory #3): an append-only, in-memory-plus-on-disk log
 * of every step this run of TestEnforcerAgent took. Kept separate from
 * Lab5.RagLogger-style free-text logging - a trace is structured data
 * meant to be replayed/audited, not just read by a human at the console.
 */
public class AgentTrace {

    private final List<TraceEvent> events = new CopyOnWriteArrayList<>();
    private final Path traceFile;

    public AgentTrace(Path traceFile) {
        this.traceFile = traceFile;
    }

    public void record(String step, String status, String detail) {
        TraceEvent event = TraceEvent.of(step, status, detail);
        events.add(event);
        appendToDisk(event);
    }

    public List<TraceEvent> events() {
        return new ArrayList<>(events);
    }

    public Path traceFile() {
        return traceFile;
    }

    private void appendToDisk(TraceEvent event) {
        try {
            if (traceFile.getParent() != null) {
                Files.createDirectories(traceFile.getParent());
            }
            Files.writeString(
                    traceFile,
                    event.toJsonLine() + System.lineSeparator(),
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            throw new UncheckedIOException("failed to append trace event to " + traceFile, e);
        }
    }
}
