package Lab5;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentTraceTest {

    @Test
    void recordAppendsToInMemoryEventList(@TempDir Path dir) {
        AgentTrace trace = new AgentTrace(dir.resolve("trace.ndjson"));

        trace.record("discover", "started", "detail1");
        trace.record("discover", "completed", "detail2");

        List<TraceEvent> events = trace.events();
        assertEquals(2, events.size());
        assertEquals("discover", events.get(0).step());
        assertEquals("started", events.get(0).status());
    }

    @Test
    void recordAlsoAppendsToDiskAsNdjson(@TempDir Path dir) throws IOException {
        Path traceFile = dir.resolve("trace.ndjson");
        AgentTrace trace = new AgentTrace(traceFile);

        trace.record("guardrail_input", "passed", "com.acme.FooService");

        List<String> lines = Files.readAllLines(traceFile);
        assertEquals(1, lines.size());
        assertTrue(lines.get(0).contains("\"step\":\"guardrail_input\""));
        assertTrue(lines.get(0).contains("\"status\":\"passed\""));
    }

    @Test
    void createsParentDirectoriesIfMissing(@TempDir Path dir) throws IOException {
        Path nested = dir.resolve("nested/deeper/trace.ndjson");
        AgentTrace trace = new AgentTrace(nested);

        trace.record("x", "y", "z");

        assertTrue(Files.exists(nested));
    }

    @Test
    void traceEventEscapesQuotesAndNewlinesInJson() {
        TraceEvent event = TraceEvent.of("step", "status", "has \"quotes\" and\nnewline");

        String json = event.toJsonLine();

        assertTrue(json.contains("\\\"quotes\\\""));
        assertTrue(json.contains("\\n"));
    }
}
