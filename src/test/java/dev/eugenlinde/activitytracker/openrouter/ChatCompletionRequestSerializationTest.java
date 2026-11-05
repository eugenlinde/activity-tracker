package dev.eugenlinde.activitytracker.openrouter;

import dev.eugenlinde.activitytracker.openrouter.ChatCompletionRequest.JsonSchema;
import dev.eugenlinde.activitytracker.openrouter.ChatCompletionRequest.Message;
import dev.eugenlinde.activitytracker.openrouter.ChatCompletionRequest.ResponseFormat;
import dev.eugenlinde.activitytracker.openrouter.ChatCompletionRequest.Role;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChatCompletionRequestSerializationTest {

    private final JsonMapper mapper = JsonMapper.builder().build();

    @Test
    void topLevelKeys_areSnakeCase() {
        JsonNode json = serialize(request());

        assertEquals("test-model", json.path("model").asString());
        assertEquals(0.2, json.path("temperature").asDouble());
        assertEquals(8000, json.path("max_tokens").asInt());
        assertTrue(json.has("response_format"));
        assertFalse(json.has("maxTokens"));
        assertFalse(json.has("responseFormat"));
    }

    @Test
    void responseFormat_nestsJsonSchemaUnderSnakeCaseKey() {
        JsonNode format = serialize(request()).path("response_format");

        assertEquals("json_schema", format.path("type").asString());
        assertFalse(format.has("jsonSchema"));
        JsonNode jsonSchema = format.path("json_schema");
        assertEquals("activity_import", jsonSchema.path("name").asString());
        assertTrue(jsonSchema.path("strict").asBoolean());
    }

    @Test
    void schema_isEmbeddedAsJsonObjectNotString() {
        JsonNode schema = serialize(request()).path("response_format").path("json_schema").path("schema");

        assertTrue(schema.isObject());
        assertEquals("object", schema.path("type").asString());
        assertEquals("string", schema.path("properties").path("version").path("type").asString());
    }

    @Test
    void messages_keepOrderRoleAndContent() {
        JsonNode messages = serialize(request()).path("messages");

        assertEquals(2, messages.size());
        assertEquals("system", messages.get(0).path("role").asString());
        assertEquals("You map CSV files.", messages.get(0).path("content").asString());
        assertEquals("user", messages.get(1).path("role").asString());
        assertEquals("a;b\n1;2", messages.get(1).path("content").asString());
    }

    @ParameterizedTest
    @CsvSource({"SYSTEM, system", "USER, user", "ASSISTANT, assistant", "TOOL, tool"})
    void role_serializesLowercase(Role role, String expected) {
        assertEquals(expected, mapper.valueToTree(role).asString());
    }

    @ParameterizedTest
    @CsvSource({"system, SYSTEM", "user, USER", "assistant, ASSISTANT", "tool, TOOL"})
    void role_deserializesFromLowercase(String json, Role expected) {
        assertEquals(expected, mapper.readValue("\"" + json + "\"", Role.class));
    }

    private ChatCompletionRequest request() {
        JsonNode schema = mapper.readTree("""
                {"type":"object","properties":{"version":{"type":"string"}},"required":["version"],"additionalProperties":false}
                """);
        return new ChatCompletionRequest(
                "test-model",
                List.of(new Message(Role.SYSTEM, "You map CSV files."), new Message(Role.USER, "a;b\n1;2")),
                new ResponseFormat("json_schema", new JsonSchema("activity_import", true, schema)),
                0.2,
                8000
        );
    }

    private JsonNode serialize(ChatCompletionRequest request) {
        return mapper.readTree(mapper.writeValueAsString(request));
    }
}
