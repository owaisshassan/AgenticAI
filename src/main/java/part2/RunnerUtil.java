package part2;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;

public class RunnerUtil {

    public static final String API_URL = part1.RunnerUtil.API_URL;
    public static final String API_KEY = part1.RunnerUtil.API_KEY;

    public static String callGateway(AgentRequest request) throws IOException, InterruptedException {
        ObjectMapper mapper = new ObjectMapper();
        String jsonBody = mapper.writeValueAsString(request);

        FileLogger.info("Outgoing gateway request: " + jsonBody);

        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(API_URL))
                .header("accept", "application/json")
                .header("x-litellm-api-key", API_KEY)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();

        HttpClient client = HttpClient.newHttpClient();
        HttpResponse<String> response;
        try {
            response = client.send(httpRequest, HttpResponse.BodyHandlers.ofString());
        } catch (IOException | InterruptedException e) {
            FileLogger.error("Gateway call threw an exception", e);
            throw e;
        }

        FileLogger.info("Incoming gateway response (" + response.statusCode() + "): " + response.body());

        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            String message = "Gateway call failed: " + response.statusCode() + " - " + response.body();
            FileLogger.error(message, new IOException(message));
            throw new IOException(message);
        }

        return response.body();
    }

    public static String getStopReason(String responseJson) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode root = mapper.readTree(responseJson);
        JsonNode stopReason = root.get("stop_reason");
        return stopReason == null ? null : stopReason.asText();
    }

    public static List<ContentBlock> getContentBlocks(String responseJson) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode root = mapper.readTree(responseJson);
        JsonNode content = root.get("content");
        List<ContentBlock> blocks = new ArrayList<>();
        if (content == null || !content.isArray()) {
            return blocks;
        }
        for (JsonNode blockNode : content) {
            ContentBlock block = mapper.treeToValue(blockNode, ContentBlock.class);
            blocks.add(block);
        }
        return blocks;
    }

    public static String extractText(String responseJson) throws IOException {
        StringBuilder text = new StringBuilder();
        for (ContentBlock block : getContentBlocks(responseJson)) {
            if ("text".equals(block.getType()) && block.getText() != null) {
                text.append(block.getText());
            }
        }
        return text.toString();
    }
}
