package part2;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

public class Runner {

    public static void main(String[] args) throws Exception {
        String system = "You are a helpful assistant that can answer questions about current weather. " +
                "Use the get_weather tool whenever the user asks about weather in a specific place.";

        List<Tool> tools = List.of(buildWeatherTool());
        List<Message> messages = new ArrayList<>();

        Scanner scanner = new Scanner(System.in);
        FileLogger.info("Session started.");

        while (true) {
            System.out.print("You: ");
            String userInput = scanner.hasNextLine() ? scanner.nextLine() : null;

            if (userInput == null || userInput.isBlank()
                    || userInput.equalsIgnoreCase("exit") || userInput.equalsIgnoreCase("quit")) {
                FileLogger.info("Blank input received, ending conversation.");
                System.out.println("Ending conversation.");
                break;
            }

            FileLogger.info("User input received: " + userInput);
            messages.add(new Message("user", userInput));

            String assistantText = resolveTurn(system, tools, messages);
            System.out.println("Agent: " + assistantText);
        }

        FileLogger.info("Session ended.");
        scanner.close();
    }

    private static String resolveTurn(String system, List<Tool> tools, List<Message> messages) throws Exception {
        while (true) {
            Agent agent = new Agent("claude-sonnet", 500, system, messages, tools);
            String response = RunnerUtil.callGateway(agent);
            String stopReason = RunnerUtil.getStopReason(response);
            List<ContentBlock> blocks = RunnerUtil.getContentBlocks(response);

            if (!"tool_use".equals(stopReason)) {
                String text = RunnerUtil.extractText(response);
                messages.add(new Message("assistant", text));
                return text;
            }

            messages.add(new Message("assistant", blocks));

            List<ContentBlock> toolResults = new ArrayList<>();
            for (ContentBlock block : blocks) {
                if (!"tool_use".equals(block.getType())) {
                    continue;
                }

                String toolName = block.getName();
                String toolUseId = block.getId();
                Map<String, Object> input = block.getInput();
                Object location = input == null ? null : input.get("location");

                FileLogger.info("Tool call requested: " + toolName + " with input " + input);

                String result;
                if ("get_weather".equals(toolName)) {
                    result = WeatherConnector.getWeather(location == null ? null : location.toString());
                } else {
                    FileLogger.warn("Unknown tool requested: " + toolName);
                    result = "Error: unknown tool \"" + toolName + "\".";
                }

                FileLogger.info("Tool result for " + toolUseId + ": " + result);
                toolResults.add(ContentBlock.toolResult(toolUseId, result));
            }

            messages.add(new Message("user", toolResults));
        }
    }

    private static Tool buildWeatherTool() {
        Map<String, Object> locationProperty = new LinkedHashMap<>();
        locationProperty.put("type", "string");
        locationProperty.put("description", "The city or place name to look up the current weather for.");

        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("location", locationProperty);

        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", properties);
        schema.put("required", List.of("location"));

        return new Tool("get_weather", "Get the current weather for a given location.", schema);
    }
}
