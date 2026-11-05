package dev.eugenlinde.activitytracker;

import dev.eugenlinde.activitytracker.config.OpenRouterProperties;
import dev.eugenlinde.activitytracker.config.StorageProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties({StorageProperties.class, OpenRouterProperties.class})
public class ActivityTrackerApplication {

    public static void main(String[] args) {
        SpringApplication.run(ActivityTrackerApplication.class, args);
    }

}
