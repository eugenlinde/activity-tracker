package dev.eugenlinde.activitytracker.garmin;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;

@Component
public class GarminClient {
    private static final String TOKEN_PATH = "/personalapitoken-service/personal-api/personal-api-key/token";
    private static final String DAILY_PATH = "/usersummary-service/usersummary/daily";

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public GarminClient(@Qualifier("garminRestClient") RestClient restClient, ObjectMapper objectMapper) {
        this.restClient = restClient;
        this.objectMapper = objectMapper;
    }

    public String fetchAccessToken(String apiKey) {
        String body = execute(() -> restClient
                .post()
                .uri(TOKEN_PATH)
                .header(HttpHeaders.AUTHORIZATION, "ApiKey " + apiKey)
                .retrieve()
                .body(String.class));

        String text = body == null ? "" : body.trim();
        if (text.startsWith("{")) {
            text = tokenFromJson(text);
        }
        if (text.isBlank()) {
            throw new GarminException("Garmin returned no access token");
        }
        return text;
    }

    public Optional<Integer> fetchDailySteps(String accessToken, LocalDate date) {
        String body = execute(() -> restClient
                .get()
                .uri(uriBuilder -> uriBuilder
                        .path(DAILY_PATH)
                        .queryParam("calendarDate", date.toString())
                        .build())
                .headers(headers -> headers.setBearerAuth(accessToken))
                .retrieve()
                .body(String.class));

        if (body == null || body.isBlank()) {
            return Optional.empty();
        }
        Map<?, ?> summary = readObject(body);
        if (summary == null) {
            return Optional.empty();
        }
        if (summary.get("totalSteps") instanceof Number total) {
            double value = total.doubleValue();
            if (value >= Integer.MAX_VALUE) {
                return Optional.of(Integer.MAX_VALUE);
            }
            if (value <= Integer.MIN_VALUE) {
                return Optional.of(Integer.MIN_VALUE);
            }
            return Optional.of((int) value);
        }
        return Optional.empty();
    }

    private String tokenFromJson(String text) {
        Map<?, ?> json = readObject(text);
        if (json != null && json.get("access_token") instanceof String token) {
            return token.trim();
        }
        return "";
    }

    private Map<?, ?> readObject(String text) {
        try {
            return objectMapper.readValue(text, Map.class);
        } catch (JacksonException | ClassCastException unreadable) {
            throw new GarminException("Garmin answer could not be read");
        }
    }

    private String execute(java.util.function.Supplier<String> call) {
        try {
            return call.get();
        } catch (RestClientResponseException responseException) {
            throw new GarminException("Garmin response status: " + responseException.getStatusCode().value());
        } catch (RestClientException requestException) {
            throw new GarminException("Garmin request failed: " + requestException.getClass().getSimpleName());
        }
    }
}
