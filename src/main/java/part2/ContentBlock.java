package part2;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class ContentBlock {
    private String type;
    private String text;
    private String id;
    private String name;
    private Map<String, Object> input;

    @JsonProperty("tool_use_id")
    private String toolUseId;

    private Object content;

    public ContentBlock() {
    }

    public static ContentBlock text(String text) {
        ContentBlock block = new ContentBlock();
        block.type = "text";
        block.text = text;
        return block;
    }

    public static ContentBlock toolResult(String toolUseId, Object content) {
        ContentBlock block = new ContentBlock();
        block.type = "tool_result";
        block.toolUseId = toolUseId;
        block.content = content;
        return block;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Map<String, Object> getInput() {
        return input;
    }

    public void setInput(Map<String, Object> input) {
        this.input = input;
    }

    public String getToolUseId() {
        return toolUseId;
    }

    public void setToolUseId(String toolUseId) {
        this.toolUseId = toolUseId;
    }

    public Object getContent() {
        return content;
    }

    public void setContent(Object content) {
        this.content = content;
    }

    @Override
    public String toString() {
        return "ContentBlock{" +
                "type='" + type + '\'' +
                ", text='" + text + '\'' +
                ", id='" + id + '\'' +
                ", name='" + name + '\'' +
                ", input=" + input +
                ", toolUseId='" + toolUseId + '\'' +
                ", content=" + content +
                '}';
    }
}
