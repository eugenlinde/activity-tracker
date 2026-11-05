package dev.eugenlinde.activitytracker.openrouter;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record ChatCompletionResponse(
        List<Choice> choices,
        Usage usage
) {
    public record Choice(
            ChatCompletionRequest.Message message,
            @JsonProperty("finish_reason") String finishReason
    ) {
    }

    public record Usage(
            @JsonProperty("prompt_tokens") Integer promptTokens,
            @JsonProperty("completion_tokens") Integer completionTokens,
            @JsonProperty("total_tokens") Integer totalTokens
    ) {
    }
}
