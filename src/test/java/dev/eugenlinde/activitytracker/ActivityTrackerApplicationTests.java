package dev.eugenlinde.activitytracker;

import dev.eugenlinde.activitytracker.config.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@Import({TestcontainersConfiguration.class, SecurityConfig.class})
@SpringBootTest
@ActiveProfiles("test")
class ActivityTrackerApplicationTests {

    @Test
    void contextLoads() {
    }

}
