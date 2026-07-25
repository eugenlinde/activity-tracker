package dev.eugenlinde.activitytracker;

import org.springframework.boot.SpringApplication;

public class TestActivityTrackerApplication {

    public static void main(String[] args) {
        SpringApplication.from(ActivityTrackerApplication::main).with(TestcontainersConfiguration.class).run(args);
    }

}
