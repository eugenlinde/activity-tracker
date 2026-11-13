package dev.eugenlinde.activitytracker;

import dev.eugenlinde.activitytracker.config.SignupProperties;
import dev.eugenlinde.activitytracker.config.StorageProperties;
import dev.eugenlinde.activitytracker.openrouter.OpenRouterProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties({StorageProperties.class, OpenRouterProperties.class, SignupProperties.class})
public class ActivityTrackerApplication {

    public static void main(String[] args) {
        SpringApplication.run(ActivityTrackerApplication.class, args);
    }

}
