package part3;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;

/**
 * Gateway call helper for part3. Reuses part1.RunnerUtil.callGateway (same
 * HttpClient + Jackson conventions, same API_URL/API_KEY) rather than
 * duplicating it, and adds response parsing part1 doesn't need: extracting
 * assistant text and token usage.
 */
public class RunnerUtil {

    public static String callGateway(part1.AgentRequest request) throws IOException, InterruptedException {
        return part1.RunnerUtil.callGateway(request);
    }

    public static String extractAssistantText(String responseJson) throws IOException {
        return part1.RunnerUtil.extractAssistantText(responseJson);
    }

    public static TokenUsage extractTokenUsage(String responseJson, String model) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode root = mapper.readTree(responseJson);
        JsonNode usage = root.get("usage");
        if (usage == null) {
            return new TokenUsage(0, 0, 0.0);
        }
        int inputTokens = usage.has("input_tokens") ? usage.get("input_tokens").asInt() : 0;
        int outputTokens = usage.has("output_tokens") ? usage.get("output_tokens").asInt() : 0;
        return TokenUsage.forModel(model, inputTokens, outputTokens);
    }
}
