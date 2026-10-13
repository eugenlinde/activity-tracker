package dev.eugenlinde.activitytracker.config;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.nio.file.Path;

@Setter
@Getter
@ConfigurationProperties(prefix = "storage")
@Validated
public class StorageProperties {

    @NotNull
    private Path rootPath;

}