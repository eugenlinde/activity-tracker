package dev.eugenlinde.activitytracker.errorreport;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Setter
@Getter
@ConfigurationProperties(prefix = "error-report")
public class ErrorReportProperties {
    private String githubToken = "";
    private String githubRepo = "";
    private String githubApiUrl = "https://api.github.com";
    private int maxPerHour = 5;

    public boolean isEnabled() {
        return githubToken != null && !githubToken.isBlank()
                && githubRepo != null && !githubRepo.isBlank();
    }
}
