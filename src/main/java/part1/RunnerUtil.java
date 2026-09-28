package part1;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public class RunnerUtil {

    public static final String API_URL = "https://13-205-72-246.nip.io/v1/messages";
    public static final String API_KEY = "sk-NSzAlcL5DQdh5YffpSv3Sw";


    public static String callGateway(AgentRequest request) throws IOException, InterruptedException {
        ObjectMapper mapper = new ObjectMapper();
        String jsonBody = mapper.writeValueAsString(request);

        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(API_URL))
                .header("accept", "application/json")
                .header("x-litellm-api-key", API_KEY)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();

        HttpClient client = HttpClient.newHttpClient();
        HttpResponse<String> response = client.send(httpRequest, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("Gateway call failed: " + response.statusCode() + " - " + response.body());
        }

        return response.body();
    }

    public static String extractAssistantText(String responseJson) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode root = mapper.readTree(responseJson);
        JsonNode content = root.get("content");
        if (content == null || !content.isArray()) {
            return responseJson;
        }

        StringBuilder text = new StringBuilder();
        for (JsonNode block : content) {
            JsonNode textNode = block.get("text");
            if (textNode != null) {
                text.append(textNode.asText());
            }
        }
        return text.toString();
    }
}
