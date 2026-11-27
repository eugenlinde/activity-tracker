package dev.eugenlinde.activitytracker.errorreport;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.net.SocketTimeoutException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class ErrorReporterTest {

    private static final String LIST_URL =
            "http://github.test/repos/owner/repo/issues?state=open&labels=prod-error&per_page=100";
    private static final String CREATE_URL = "http://github.test/repos/owner/repo/issues";
    private static final String SECRET_WORD = "zebrafinch";

    private final MutableClock clock = new MutableClock(Instant.parse("2026-11-27T10:00:00Z"));
    private ErrorReportProperties properties;
    private MockRestServiceServer server;
    private ErrorReporter reporter;

    @BeforeEach
    void setUp() {
        properties = new ErrorReportProperties();
        properties.setGithubToken("test-token");
        properties.setGithubRepo("owner/repo");
        properties.setGithubApiUrl("http://github.test");
        properties.setMaxPerHour(5);
        build();
    }

    private void build() {
        RestClient configured = new ErrorReportClientConfig().errorReportRestClient(RestClient.builder(), properties);
        RestClient.Builder builder = configured.mutate();
        server = MockRestServiceServer.bindTo(builder).build();
        reporter = new ErrorReporter(builder.build(), properties, clock);
    }

    private void expectEmptyList() {
        server.expect(requestTo(LIST_URL))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));
    }

    private void expectCreate() {
        server.expect(requestTo(CREATE_URL))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(org.springframework.http.HttpStatus.CREATED));
    }

    private static RuntimeException failWithMessage() {
        return new IllegalStateException(SECRET_WORD + " outer", new IllegalArgumentException(SECRET_WORD + " inner"));
    }

    private static RuntimeException thrownFromLine(int which) {
        if (which == 1) {
            return new IllegalStateException("one");
        }
        return new IllegalStateException("two");
    }

    private static RuntimeException otherTypeFromSameMethod(boolean other) {
        if (other) {
            return new IllegalArgumentException("x");
        }
        return new IllegalStateException("x");
    }

    @Test
    void unexpectedError_opensOneIssueWithAllParts() {
        server.expect(requestTo(LIST_URL))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("Authorization", "Bearer test-token"))
                .andExpect(header("Accept", "application/vnd.github+json"))
                .andExpect(header("X-GitHub-Api-Version", "2022-11-28"))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));
        RuntimeException error = failWithMessage();
        server.expect(requestTo(CREATE_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer test-token"))
                .andExpect(jsonPath("$.title")
                        .value("Prod error: IllegalStateException at ErrorReporterTest.failWithMessage"))
                .andExpect(jsonPath("$.labels[0]").value("prod-error"))
                .andExpect(jsonPath("$.labels.length()").value(1))
                .andExpect(jsonPath("$.body", containsString("answered 500 for an unexpected error at 2026-11-27T10:00:00Z")))
                .andExpect(jsonPath("$.body", containsString("Request: GET /api/activities/{id}")))
                .andExpect(jsonPath("$.body", containsString("Exception: java.lang.IllegalStateException")))
                .andExpect(jsonPath("$.body", containsString("Caused by: java.lang.IllegalArgumentException")))
                .andExpect(jsonPath("$.body", containsString("```\n")))
                .andExpect(jsonPath("$.body", containsString(
                        "dev.eugenlinde.activitytracker.errorreport.ErrorReporterTest.failWithMessage:")))
                .andExpect(jsonPath("$.body", containsString("Fingerprint: " + ErrorReporter.fingerprint(error))))
                .andExpect(jsonPath("$.body", containsString(
                        "Find the cause, fix it, and add a test that reproduces it.")))
                .andRespond(withStatus(org.springframework.http.HttpStatus.CREATED));

        reporter.reportNow(error, "GET", "/api/activities/{id}");

        server.verify();
    }

    @Test
    void messagesAreNeverSent() {
        expectEmptyList();
        server.expect(requestTo(CREATE_URL))
                .andExpect(content().string(not(containsString(SECRET_WORD))))
                .andRespond(withStatus(org.springframework.http.HttpStatus.CREATED));

        reporter.reportNow(failWithMessage(), "GET", "/api/activities/{id}");

        server.verify();
    }

    @Test
    void realPathAndQueryAreNeverSent_routePatternIs() {
        expectEmptyList();
        server.expect(requestTo(CREATE_URL))
                .andExpect(content().string(not(containsString("/api/activities/42"))))
                .andExpect(content().string(not(containsString("secret=1"))))
                .andExpect(jsonPath("$.body", containsString("GET /api/activities/{id}")))
                .andRespond(withStatus(org.springframework.http.HttpStatus.CREATED));

        reporter.reportNow(failWithMessage(), "GET", "/api/activities/{id}");

        server.verify();
    }

    @Test
    void missingRoute_isUnknown() {
        expectEmptyList();
        server.expect(requestTo(CREATE_URL))
                .andExpect(jsonPath("$.body", containsString("Request: POST unknown")))
                .andRespond(withStatus(org.springframework.http.HttpStatus.CREATED));

        reporter.reportNow(failWithMessage(), "POST", null);

        server.verify();
    }

    @Test
    void blankToken_makesNoCall() {
        properties.setGithubToken(" ");
        build();

        assertDoesNotThrow(() -> reporter.reportNow(failWithMessage(), "GET", "/x"));
        assertDoesNotThrow(() -> reporter.report(failWithMessage(), "GET", "/x"));

        server.verify();
    }

    @Test
    void blankRepo_makesNoCall() {
        properties.setGithubRepo("");
        build();

        assertDoesNotThrow(() -> reporter.reportNow(failWithMessage(), "GET", "/x"));

        server.verify();
    }

    @Test
    void openIssueWithSameFingerprint_opensNothing() {
        RuntimeException error = failWithMessage();
        String listed = "[{\"body\":\"text\\n\\nFingerprint: " + ErrorReporter.fingerprint(error) + "\\n\\nmore\"}]";
        server.expect(requestTo(LIST_URL))
                .andRespond(withSuccess(listed, MediaType.APPLICATION_JSON));

        reporter.reportNow(error, "GET", "/x");

        server.verify();
    }

    @Test
    void openIssueWithOtherFingerprint_stillOpens() {
        server.expect(requestTo(LIST_URL))
                .andRespond(withSuccess("[{\"body\":\"Fingerprint: 0000000000000000\"},{\"body\":null}]",
                        MediaType.APPLICATION_JSON));
        expectCreate();

        reporter.reportNow(failWithMessage(), "GET", "/x");

        server.verify();
    }

    @Test
    void listFails_opensNothingAndThrowsNothing() {
        server.expect(requestTo(LIST_URL)).andRespond(withServerError());

        assertDoesNotThrow(() -> reporter.reportNow(failWithMessage(), "GET", "/x"));

        server.verify();
    }

    @Test
    void createFails_throwsNothing() {
        expectEmptyList();
        server.expect(requestTo(CREATE_URL)).andRespond(withServerError());

        assertDoesNotThrow(() -> reporter.reportNow(failWithMessage(), "GET", "/x"));

        server.verify();
    }

    @Test
    void createTimesOut_throwsNothing() {
        expectEmptyList();
        server.expect(requestTo(CREATE_URL)).andRespond(withException(new SocketTimeoutException("timeout")));

        assertDoesNotThrow(() -> reporter.reportNow(failWithMessage(), "GET", "/x"));

        server.verify();
    }

    @Test
    void limit_sixthReportInAnHourMakesNoCall_andAfterTheHourItGoesOutAgain() {
        for (int i = 0; i < 5; i++) {
            expectEmptyList();
            expectCreate();
        }
        for (int i = 0; i < 5; i++) {
            reporter.reportNow(failWithMessage(), "GET", "/x");
        }
        server.verify();

        server.reset();
        clock.advance(Duration.ofMinutes(59));
        reporter.reportNow(failWithMessage(), "GET", "/x");
        server.verify();

        server.reset();
        clock.advance(Duration.ofMinutes(2));
        expectEmptyList();
        expectCreate();
        reporter.reportNow(failWithMessage(), "GET", "/x");
        server.verify();
    }

    @Test
    void fingerprint_differsForDifferentExceptionsFromSameMethod() {
        assertNotEquals(
                ErrorReporter.fingerprint(otherTypeFromSameMethod(true)),
                ErrorReporter.fingerprint(otherTypeFromSameMethod(false))
        );
    }

    @Test
    void fingerprint_isTheSameForTheSameExceptionFromTwoLines() {
        String first = ErrorReporter.fingerprint(thrownFromLine(1));
        String second = ErrorReporter.fingerprint(thrownFromLine(2));

        assertEquals(first, second);
        assertEquals(16, first.length());
    }

    private static final class MutableClock extends Clock {
        private Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
