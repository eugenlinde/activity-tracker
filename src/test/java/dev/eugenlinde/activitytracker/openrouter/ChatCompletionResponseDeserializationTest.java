package dev.eugenlinde.activitytracker.openrouter;

import dev.eugenlinde.activitytracker.openrouter.ChatCompletionRequest.Role;
import dev.eugenlinde.activitytracker.openrouter.ChatCompletionResponse.Choice;
import org.junit.jupiter.api.Test;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ChatCompletionResponseDeserializationTest {

    private static final String SUCCESS = """
            {
              "id": "gen-123",
              "object": "chat.completion",
              "created": 1762300000,
              "model": "some/model",
              "provider": "SomeProvider",
              "system_fingerprint": null,
              "choices": [
                {
                  "index": 0,
                  "logprobs": null,
                  "finish_reason": "stop",
                  "native_finish_reason": "stop",
                  "message": {
                    "role": "assistant",
                    "content": "{\\"activities\\":[{\\"name\\":\\"Kükk\\"}]}",
                    "refusal": null,
                    "reasoning": null
                  }
                }
              ],
              "usage": {
                "prompt_tokens": 1200,
                "completion_tokens": 900,
                "total_tokens": 2100,
                "prompt_tokens_details": { "cached_tokens": 0 }
              }
            }
            """;

    private final JsonMapper mapper = JsonMapper.builder().build();

    @Test
    void realisticPayload_withUnmodelledFields_parses() {
        ChatCompletionResponse response = mapper.readValue(SUCCESS, ChatCompletionResponse.class);

        assertEquals(1, response.choices().size());
    }

    @Test
    void content_staysRawJsonString() {
        Choice choice = mapper.readValue(SUCCESS, ChatCompletionResponse.class).choices().getFirst();

        assertEquals("{\"activities\":[{\"name\":\"Kükk\"}]}", choice.message().content());
    }

    @Test
    void message_roleMapsToAssistant() {
        Choice choice = mapper.readValue(SUCCESS, ChatCompletionResponse.class).choices().getFirst();

        assertEquals(Role.ASSISTANT, choice.message().role());
    }

    @Test
    void finishReason_mapsFromSnakeCase() {
        assertEquals("stop", mapper.readValue(SUCCESS, ChatCompletionResponse.class).choices().getFirst().finishReason());
        assertEquals("length", parse(choiceJson("length", "\"{\\\"activities\\\":[\"")).choices().getFirst().finishReason());
    }

    @Test
    void finishReason_unknownValue_isKeptAsString() {
        assertEquals("provider_specific", parse(choiceJson("provider_specific", "\"{}\"")).choices().getFirst().finishReason());
    }

    @Test
    void content_null_isNull() {
        Choice choice = parse(choiceJson("content_filter", "null")).choices().getFirst();

        assertNull(choice.message().content());
        assertEquals("content_filter", choice.finishReason());
    }

    @Test
    void usage_mapsFromSnakeCase() {
        ChatCompletionResponse.Usage usage = mapper.readValue(SUCCESS, ChatCompletionResponse.class).usage();

        assertEquals(1200, usage.promptTokens());
        assertEquals(900, usage.completionTokens());
        assertEquals(2100, usage.totalTokens());
    }

    @Test
    void usage_missing_isNull() {
        assertNull(parse(choiceJson("stop", "\"{}\"")).usage());
    }

    @Test
    void emptyChoices_parsesToEmptyList() {
        assertEquals(0, parse("{\"choices\":[]}").choices().size());
    }

    @Test
    void unknownRole_failsDeserialization() {
        String json = """
                {"choices":[{"finish_reason":"stop","message":{"role":"developer","content":"{}"}}]}
                """;

        assertThrows(JacksonException.class, () -> parse(json));
    }

    private ChatCompletionResponse parse(String json) {
        return mapper.readValue(json, ChatCompletionResponse.class);
    }

    private static String choiceJson(String finishReason, String contentLiteral) {
        return """
                {"choices":[{"finish_reason":"%s","message":{"role":"assistant","content":%s}}]}
                """.formatted(finishReason, contentLiteral);
    }
}
