package dev.eugenlinde.activitytracker.garmin;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;

@Configuration
public class GarminClientConfig {

    @Bean
    public RestClient garminRestClient(RestClient.Builder builder, GarminProperties garminProperties) {
        var httpClient = HttpClient.newBuilder()
                .connectTimeout(garminProperties.getTimeout())
                .build();

        var factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(garminProperties.getTimeout());

        return builder
                .baseUrl(garminProperties.getBaseUrl())
                .requestFactory(factory)
                .defaultHeader(HttpHeaders.ACCEPT, "*/*")
                .build();
    }
}
