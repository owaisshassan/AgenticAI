package part2;

import java.util.List;

public class Agent extends AgentRequest {
    public Agent() {
        super();
    }

    public Agent(String model, Integer maxTokens, String system, List<Message> messages, List<Tool> tools) {
        super(model, maxTokens, system, messages, tools);
    }
}
