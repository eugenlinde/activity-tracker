package dev.eugenlinde.activitytracker.garmin;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

import java.net.SocketTimeoutException;
import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class GarminClientTest {
    private static final String API_KEY = "the-secret-api-key";
    private static final String TOKEN = "the-secret-access-token";
    private static final LocalDate DATE = LocalDate.parse("2026-11-28");
    private static final String TOKEN_URL =
            "http://garmin.test/personalapitoken-service/personal-api/personal-api-key/token";
    private static final String DAILY_URL =
            "http://garmin.test/usersummary-service/usersummary/daily?calendarDate=2026-11-28";
    private static final String LEAKY_BODY = "{\"detail\":\"leaked-response-body\"}";

    private MockRestServiceServer server;
    private GarminClient client;

    @BeforeEach
    void setUp() {
        GarminProperties properties = new GarminProperties();
        properties.setBaseUrl("http://garmin.test");

        RestClient configured = new GarminClientConfig().garminRestClient(RestClient.builder(), properties);
        RestClient.Builder builder = configured.mutate();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new GarminClient(builder.build(), JsonMapper.builder().build());
    }

    @AfterEach
    void verifyServer() {
        server.verify();
    }

    @Test
    void fetchAccessToken_postsToTheTokenPathWithTheApiKeyScheme() {
        server.expect(requestTo(TOKEN_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "ApiKey " + API_KEY))
                .andRespond(withSuccess(TOKEN, MediaType.TEXT_PLAIN));

        assertEquals(TOKEN, client.fetchAccessToken(API_KEY));
    }

    @Test
    void fetchAccessToken_trimsAPlainTextToken() {
        server.expect(requestTo(TOKEN_URL)).andRespond(withSuccess("  " + TOKEN + "\n", MediaType.TEXT_PLAIN));

        assertEquals(TOKEN, client.fetchAccessToken(API_KEY));
    }

    @Test
    void fetchAccessToken_readsAccessTokenFromAJsonBody() {
        server.expect(requestTo(TOKEN_URL)).andRespond(withSuccess(
                "{\"access_token\":\"" + TOKEN + "\",\"expires_in\":3600}", MediaType.APPLICATION_JSON));

        assertEquals(TOKEN, client.fetchAccessToken(API_KEY));
    }

    @Test
    void fetchAccessToken_withJsonLackingAccessToken_throws() {
        server.expect(requestTo(TOKEN_URL)).andRespond(withSuccess("{\"other\":1}", MediaType.APPLICATION_JSON));

        assertThrows(GarminException.class, () -> client.fetchAccessToken(API_KEY));
    }

    @Test
    void fetchAccessToken_withEmptyBody_throws() {
        server.expect(requestTo(TOKEN_URL)).andRespond(withSuccess());

        assertThrows(GarminException.class, () -> client.fetchAccessToken(API_KEY));
    }

    @Test
    void fetchAccessToken_on401_throwsWithStatusOnly() {
        server.expect(requestTo(TOKEN_URL)).andRespond(withStatus(HttpStatus.UNAUTHORIZED)
                .contentType(MediaType.APPLICATION_JSON).body(LEAKY_BODY));

        GarminException exception = assertThrows(GarminException.class, () -> client.fetchAccessToken(API_KEY));

        assertTrue(exception.getMessage().contains("401"), exception.getMessage());
        assertSafe(exception);
    }

    @Test
    void fetchDailySteps_sendsDateAcceptAnyAndTheToken() {
        server.expect(requestTo(DAILY_URL))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(HttpHeaders.ACCEPT, "*/*"))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer " + TOKEN))
                .andRespond(withSuccess("{\"totalSteps\":8123}", MediaType.APPLICATION_JSON));

        assertEquals(Optional.of(8123), client.fetchDailySteps(TOKEN, DATE));
    }

    @Test
    void fetchDailySteps_ignoresOtherFields() {
        server.expect(requestTo(DAILY_URL)).andRespond(withSuccess(
                "{\"calendarDate\":\"2026-11-28\",\"totalKilocalories\":2100.5,\"totalSteps\":42,\"x\":[1,2]}",
                MediaType.APPLICATION_JSON));

        assertEquals(Optional.of(42), client.fetchDailySteps(TOKEN, DATE));
    }

    @Test
    void fetchDailySteps_on204_isEmpty() {
        server.expect(requestTo(DAILY_URL)).andRespond(withStatus(HttpStatus.NO_CONTENT));

        assertEquals(Optional.empty(), client.fetchDailySteps(TOKEN, DATE));
    }

    @Test
    void fetchDailySteps_withEmptyBody_isEmpty() {
        server.expect(requestTo(DAILY_URL)).andRespond(withSuccess());

        assertEquals(Optional.empty(), client.fetchDailySteps(TOKEN, DATE));
    }

    @Test
    void fetchDailySteps_withNullBody_isEmpty() {
        server.expect(requestTo(DAILY_URL)).andRespond(withSuccess("null", MediaType.APPLICATION_JSON));

        assertEquals(Optional.empty(), client.fetchDailySteps(TOKEN, DATE));
    }

    @Test
    void fetchDailySteps_withMissingTotalSteps_isEmpty() {
        server.expect(requestTo(DAILY_URL)).andRespond(withSuccess("{\"other\":5}", MediaType.APPLICATION_JSON));

        assertEquals(Optional.empty(), client.fetchDailySteps(TOKEN, DATE));
    }

    @Test
    void fetchDailySteps_withNullTotalSteps_isEmpty() {
        server.expect(requestTo(DAILY_URL)).andRespond(withSuccess("{\"totalSteps\":null}", MediaType.APPLICATION_JSON));

        assertEquals(Optional.empty(), client.fetchDailySteps(TOKEN, DATE));
    }

    @Test
    void fetchDailySteps_withUnreadableBody_throwsWithoutTheBody() {
        server.expect(requestTo(DAILY_URL)).andRespond(withSuccess("{not json " + TOKEN, MediaType.APPLICATION_JSON));

        GarminException exception = assertThrows(GarminException.class, () -> client.fetchDailySteps(TOKEN, DATE));

        assertSafe(exception);
    }

    @Test
    void fetchDailySteps_on401_throwsWithoutKeyTokenOrBody() {
        server.expect(requestTo(DAILY_URL)).andRespond(withStatus(HttpStatus.UNAUTHORIZED)
                .contentType(MediaType.APPLICATION_JSON).body(LEAKY_BODY));

        GarminException exception = assertThrows(GarminException.class, () -> client.fetchDailySteps(TOKEN, DATE));

        assertTrue(exception.getMessage().contains("401"), exception.getMessage());
        assertSafe(exception);
    }

    @Test
    void fetchDailySteps_on500_throwsWithoutKeyTokenOrBody() {
        server.expect(requestTo(DAILY_URL)).andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR)
                .contentType(MediaType.APPLICATION_JSON).body(LEAKY_BODY));

        GarminException exception = assertThrows(GarminException.class, () -> client.fetchDailySteps(TOKEN, DATE));

        assertTrue(exception.getMessage().contains("500"), exception.getMessage());
        assertSafe(exception);
    }

    @Test
    void fetchDailySteps_onTimeout_throwsWithoutKeyTokenOrBody() {
        server.expect(requestTo(DAILY_URL)).andRespond(withException(new SocketTimeoutException("Read timed out " + TOKEN)));

        GarminException exception = assertThrows(GarminException.class, () -> client.fetchDailySteps(TOKEN, DATE));

        assertSafe(exception);
    }

    private static void assertSafe(GarminException exception) {
        String message = String.valueOf(exception.getMessage());
        assertFalse(message.contains(API_KEY), message);
        assertFalse(message.contains(TOKEN), message);
        assertFalse(message.contains("leaked-response-body"), message);
        assertEquals(null, exception.getCause());
    }
}
