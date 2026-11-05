package dev.eugenlinde.activitytracker.openrouter;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;

@Configuration
public class OpenRouterClientConfig {

    @Bean
    public RestClient openRouterRestClient(RestClient.Builder builder, OpenRouterProperties openRouterProperties) {
        var httpClient = HttpClient.newBuilder()
                .connectTimeout(openRouterProperties.getTimeout())
                .build();

        var factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(openRouterProperties.getTimeout());

        return builder
                .baseUrl(openRouterProperties.getBaseUrl())
                .requestFactory(factory)
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + openRouterProperties.getApiKey())
                .build();
    }
}
