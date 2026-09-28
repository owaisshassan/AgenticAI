package part1;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RunnerUtilTest {


    @Test
    void extractAssistantText_concatenatesTextBlocks() throws IOException {
        String json = "{\"content\":[{\"type\":\"text\",\"text\":\"Hello, \"},{\"type\":\"text\",\"text\":\"world!\"}]}";
        assertEquals("Hello, world!", RunnerUtil.extractAssistantText(json));
    }

    @Test
    void extractAssistantText_singleBlock() throws IOException {
        String json = "{\"content\":[{\"type\":\"text\",\"text\":\"only block\"}]}";
        assertEquals("only block", RunnerUtil.extractAssistantText(json));
    }

    @Test
    void extractAssistantText_emptyContentArray_returnsEmptyString() throws IOException {
        String json = "{\"content\":[]}";
        assertEquals("", RunnerUtil.extractAssistantText(json));
    }

    @Test
    void extractAssistantText_missingContentField_returnsWholeJson() throws IOException {
        String json = "{\"foo\":\"bar\"}";
        assertEquals(json, RunnerUtil.extractAssistantText(json));
    }

    @Test
    void extractAssistantText_contentNotArray_returnsWholeJson() throws IOException {
        String json = "{\"content\":\"not-an-array\"}";
        assertEquals(json, RunnerUtil.extractAssistantText(json));
    }

    @Test
    void extractAssistantText_blockWithoutTextField_isSkipped() throws IOException {
        String json = "{\"content\":[{\"type\":\"image\"},{\"type\":\"text\",\"text\":\"kept\"}]}";
        assertEquals("kept", RunnerUtil.extractAssistantText(json));
    }

    @Test
    void extractAssistantText_invalidJson_throwsIOException() {
        String malformed = "{not valid json";
        assertThrows(IOException.class, () -> RunnerUtil.extractAssistantText(malformed));
    }

    @Test
    void extractAssistantText_nullInput_throwsException() {
        assertThrows(Exception.class, () -> RunnerUtil.extractAssistantText(null));
    }

    // --- callGateway ---
    @Test
    @Tag("integration")
    void callGateway_liveEndpoint_returnsSuccessfulResponse() throws IOException, InterruptedException {
        AgentRequest request = new AgentRequest(
                "claude-sonnet",
                50,
                "You are a test.",
                List.of(new Message("user", "Reply with the single word: pong"))
        );

        String response = RunnerUtil.callGateway(request);

        assertNotNull(response);
        assertFalse(response.isBlank());
    }
}
