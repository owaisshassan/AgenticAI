package part1;

import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

public class Runner {

    public static void main(String[] args) throws Exception {
        String system = "Java backend interview";
        List<Message> messages = new ArrayList<>();
        messages.add(new Message("user",
                "You are a Java backend developer with 8+ years of experience, currently " +
                        "sitting in a technical interview for a Senior Backend Engineer position. " +
                        "Ask me one interview question at a time, wait for my answer, then ask the next question."
        ));

        Scanner scanner = new Scanner(System.in);

        while (true) {
            Agent agent = new Agent("claude-sonnet", 500, system, messages);

            String response = RunnerUtil.callGateway(agent);
            String assistantText = RunnerUtil.extractAssistantText(response);

            System.out.println("Agent: " + assistantText);
            messages.add(new Message("assistant", assistantText));

            System.out.print("You: ");
            String userInput = scanner.nextLine();

            if (userInput == null || userInput.isBlank()
                    || userInput.equalsIgnoreCase("exit") || userInput.equalsIgnoreCase("quit")) {
                System.out.println("Ending conversation.");
                break;
            }

            messages.add(new Message("user", userInput));
        }

        scanner.close();
    }
}
