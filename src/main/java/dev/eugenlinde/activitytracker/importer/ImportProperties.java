package dev.eugenlinde.activitytracker.importer;

import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Setter
@Getter
@ConfigurationProperties(prefix = "import")
@Validated
public class ImportProperties {
    @Min(1)
    private int maxTextBytes = 200_000;
    @Min(1)
    private int dailyPreviewLimit = 20;
    @Min(1)
    private int analysisRows = 40;
}
