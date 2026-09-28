package part2;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

public class Tool {
    private String name;
    private String description;

    @JsonProperty("input_schema")
    private Map<String, Object> inputSchema;

    public Tool() {
    }

    public Tool(String name, String description, Map<String, Object> inputSchema) {
        this.name = name;
        this.description = description;
        this.inputSchema = inputSchema;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Map<String, Object> getInputSchema() {
        return inputSchema;
    }

    public void setInputSchema(Map<String, Object> inputSchema) {
        this.inputSchema = inputSchema;
    }
}
