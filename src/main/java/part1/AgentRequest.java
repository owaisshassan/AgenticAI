package part1;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public class AgentRequest {
    private String model;

    @JsonProperty("max_tokens")
    private Integer maxTokens;

    private String system;
    private List<Message> messages;

    public AgentRequest() {
    }

    public AgentRequest(String model, Integer maxTokens, String system, List<Message> messages) {
        this.model = model;
        this.maxTokens = maxTokens;
        this.system = system;
        this.messages = messages;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public Integer getMaxTokens() {
        return maxTokens;
    }

    public void setMaxTokens(Integer maxTokens) {
        this.maxTokens = maxTokens;
    }

    public String getSystem() {
        return system;
    }

    public void setSystem(String system) {
        this.system = system;
    }

    public List<Message> getMessages() {
        return messages;
    }

    public void setMessages(List<Message> messages) {
        this.messages = messages;
    }

    @Override
    public String toString() {
        return "AgentRequest{" +
                "model='" + model + '\'' +
                ", maxTokens=" + maxTokens +
                ", system='" + system + '\'' +
                ", messages=" + messages +
                '}';
    }
}
