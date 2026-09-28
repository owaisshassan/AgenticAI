package part2;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class AgentRequest {
    private String model;

    @JsonProperty("max_tokens")
    private Integer maxTokens;

    private String system;
    private List<Message> messages;
    private List<Tool> tools;

    public AgentRequest() {
    }

    public AgentRequest(String model, Integer maxTokens, String system, List<Message> messages, List<Tool> tools) {
        this.model = model;
        this.maxTokens = maxTokens;
        this.system = system;
        this.messages = messages;
        this.tools = tools;
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

    public List<Tool> getTools() {
        return tools;
    }

    public void setTools(List<Tool> tools) {
        this.tools = tools;
    }

    @Override
    public String toString() {
        return "AgentRequest{" +
                "model='" + model + '\'' +
                ", maxTokens=" + maxTokens +
                ", system='" + system + '\'' +
                ", messages=" + messages +
                ", tools=" + tools +
                '}';
    }
}
