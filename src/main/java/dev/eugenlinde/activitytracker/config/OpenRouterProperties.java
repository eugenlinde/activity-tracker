package dev.eugenlinde.activitytracker.config;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Setter
@Getter
@ConfigurationProperties(prefix = "openrouter")
@Validated
public class OpenRouterProperties {
    private Duration timeout = Duration.ofSeconds(60);
    @NotBlank
    private String baseUrl;
    @NotBlank
    private String apiKey;
    @NotBlank
    private String model;
}
