package dev.eugenlinde.activitytracker.errorreport;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;

@Configuration
public class ErrorReportClientConfig {

    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    @Bean
    public RestClient errorReportRestClient(RestClient.Builder builder, ErrorReportProperties properties) {
        var httpClient = HttpClient.newBuilder()
                .connectTimeout(TIMEOUT)
                .build();

        var factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(TIMEOUT);

        return builder
                .baseUrl(properties.getGithubApiUrl())
                .requestFactory(factory)
                .defaultHeaders(headers -> {
                    headers.set(HttpHeaders.ACCEPT, "application/vnd.github+json");
                    headers.set("X-GitHub-Api-Version", "2022-11-28");
                    if (properties.getGithubToken() != null && !properties.getGithubToken().isBlank()) {
                        headers.setBearerAuth(properties.getGithubToken());
                    }
                })
                .build();
    }
}
