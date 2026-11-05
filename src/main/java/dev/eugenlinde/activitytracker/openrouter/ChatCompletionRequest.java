package dev.eugenlinde.activitytracker.openrouter;

import com.fasterxml.jackson.annotation.JsonProperty;
import tools.jackson.databind.JsonNode;

import java.util.List;

public record ChatCompletionRequest(
        String model,
        List<Message> messages,
        @JsonProperty("response_format") ResponseFormat responseFormat,
        Double temperature,
        @JsonProperty("max_tokens") Integer maxTokens

) {
    public enum Role {
        @JsonProperty("system") SYSTEM,
        @JsonProperty("user") USER,
        @JsonProperty("assistant") ASSISTANT,
        @JsonProperty("tool") TOOL
    }

    public record Message(
            Role role,
            String content
    ) {
    }

    public record ResponseFormat(
            String type,
            @JsonProperty("json_schema") JsonSchema jsonSchema) {
    }

    public record JsonSchema(String name, Boolean strict, JsonNode schema) {
    }
}
