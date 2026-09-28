package part3;

import java.util.List;

public class Agent extends part1.AgentRequest {
    public Agent() {
        super();
    }

    public Agent(String model, Integer maxTokens, String system, List<part1.Message> messages) {
        super(model, maxTokens, system, messages);
    }
}
