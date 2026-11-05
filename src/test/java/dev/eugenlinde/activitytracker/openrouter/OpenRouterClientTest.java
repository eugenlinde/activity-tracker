package dev.eugenlinde.activitytracker.openrouter;

import dev.eugenlinde.activitytracker.openrouter.ChatCompletionRequest.Message;
import dev.eugenlinde.activitytracker.openrouter.ChatCompletionRequest.Role;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.net.SocketTimeoutException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class OpenRouterClientTest {

    private static final String URL = "http://openrouter.test/api/v1/chat/completions";

    private static final String SUCCESS_BODY = """
            {
              "id": "gen-1",
              "choices": [
                {
                  "finish_reason": "stop",
                  "message": { "role": "assistant", "content": "{\\"activities\\":[]}" }
                }
              ],
              "usage": { "prompt_tokens": 10, "completion_tokens": 5, "total_tokens": 15 }
            }
            """;

    private MockRestServiceServer server;
    private OpenRouterClient client;

    @BeforeEach
    void setUp() {
        OpenRouterProperties properties = new OpenRouterProperties();
        properties.setBaseUrl("http://openrouter.test/api/v1");
        properties.setApiKey("test-key");
        properties.setModel("test-model");

        RestClient configured = new OpenRouterClientConfig().openRouterRestClient(RestClient.builder(), properties);
        RestClient.Builder builder = configured.mutate();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new OpenRouterClient(builder.build());
    }

    @AfterEach
    void verifyServer() {
        server.verify();
    }

    @Test
    void call_postsJsonToChatCompletionsWithAuthHeaders() {
        server.expect(requestTo(URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer test-key"))
                .andExpect(header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.model").value("test-model"))
                .andExpect(jsonPath("$.messages[0].role").value("system"))
                .andExpect(jsonPath("$.messages[1].content").value("a;b"))
                .andExpect(jsonPath("$.max_tokens").value(8000))
                .andRespond(withSuccess(SUCCESS_BODY, MediaType.APPLICATION_JSON));

        client.call(request());
    }

    @Test
    void call_success_returnsParsedResponse() {
        server.expect(requestTo(URL)).andRespond(withSuccess(SUCCESS_BODY, MediaType.APPLICATION_JSON));

        ChatCompletionResponse response = client.call(request());

        assertEquals("{\"activities\":[]}", response.choices().getFirst().message().content());
        assertEquals("stop", response.choices().getFirst().finishReason());
        assertEquals(15, response.usage().totalTokens());
    }

    @Test
    void call_401_throwsWithStatusAndProviderMessage() {
        server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.UNAUTHORIZED)
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"error\":{\"code\":401,\"message\":\"No auth credentials found\"}}"));

        OpenRouterException exception = assertThrows(OpenRouterException.class, () -> client.call(request()));

        assertTrue(exception.getMessage().contains("401"), exception.getMessage());
        assertTrue(exception.getMessage().contains("No auth credentials found"), exception.getMessage());
        assertInstanceOf(HttpClientErrorException.Unauthorized.class, exception.getCause());
    }

    @Test
    void call_429_throwsWithStatus() {
        server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS)
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"error\":{\"code\":429,\"message\":\"Rate limit exceeded\"}}"));

        OpenRouterException exception = assertThrows(OpenRouterException.class, () -> client.call(request()));

        assertTrue(exception.getMessage().contains("429"), exception.getMessage());
    }

    @Test
    void call_500_throwsWithStatus() {
        server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.BAD_GATEWAY));

        OpenRouterException exception = assertThrows(OpenRouterException.class, () -> client.call(request()));

        assertTrue(exception.getMessage().contains("502"), exception.getMessage());
        assertInstanceOf(HttpServerErrorException.class, exception.getCause());
    }

    @Test
    void call_timeout_throwsOpenRouterException() {
        server.expect(requestTo(URL)).andRespond(withException(new SocketTimeoutException("Read timed out")));

        OpenRouterException exception = assertThrows(OpenRouterException.class, () -> client.call(request()));

        assertTrue(exception.getMessage().contains("Read timed out"), exception.getMessage());
        ResourceAccessException cause = assertInstanceOf(ResourceAccessException.class, exception.getCause());
        assertInstanceOf(SocketTimeoutException.class, cause.getCause());
    }

    @Test
    void call_malformedJson_throwsOpenRouterException() {
        server.expect(requestTo(URL)).andRespond(withSuccess("{not json", MediaType.APPLICATION_JSON));

        OpenRouterException exception = assertThrows(OpenRouterException.class, () -> client.call(request()));

        assertInstanceOf(RestClientException.class, exception.getCause());
    }

    @Test
    void call_emptyBody_throwsOpenRouterException() {
        server.expect(requestTo(URL)).andRespond(withSuccess());

        OpenRouterException exception = assertThrows(OpenRouterException.class, () -> client.call(request()));

        assertTrue(exception.getMessage().contains("null"), exception.getMessage());
    }

    private static ChatCompletionRequest request() {
        return new ChatCompletionRequest(
                "test-model",
                List.of(new Message(Role.SYSTEM, "Map the CSV."), new Message(Role.USER, "a;b")),
                null,
                0.0,
                8000
        );
    }
}
