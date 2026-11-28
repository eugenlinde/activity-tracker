package dev.eugenlinde.activitytracker.garmin;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Setter
@Getter
@ConfigurationProperties(prefix = "garmin")
@Validated
public class GarminProperties {
    @NotBlank
    private String baseUrl = "https://personal.connectapi.garmin.com";
    private Duration timeout = Duration.ofSeconds(10);
    private String syncCron = "0 0 * * * *";
}
