package dev.eugenlinde.activitytracker.openrouter;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Component
public class OpenRouterClient {
    private final RestClient restClient;

    public OpenRouterClient(@Qualifier("openRouterRestClient") RestClient restClient) {
        this.restClient = restClient;
    }

    public ChatCompletionResponse call(ChatCompletionRequest chatCompletionRequest) {
        ChatCompletionResponse chatCompletionResponse;

        try {
            chatCompletionResponse = restClient
                    .post()
                    .uri("/chat/completions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(chatCompletionRequest)
                    .retrieve()
                    .body(ChatCompletionResponse.class);
        } catch (RestClientResponseException restClientResponseException) {
            throw new OpenRouterException(
                    "OpenRouter response status: "
                            + restClientResponseException.getStatusCode()
                            + ": "
                            + restClientResponseException.getResponseBodyAsString(),
                    restClientResponseException
            );
        } catch (RestClientException restClientException) {
            throw new OpenRouterException(
                    "OpenRouter POST request failed: " + restClientException.getMessage(),
                    restClientException
            );
        }

        if (chatCompletionResponse == null) {
            throw new OpenRouterException("Chat completion response is null");
        }

        return chatCompletionResponse;
    }
}
