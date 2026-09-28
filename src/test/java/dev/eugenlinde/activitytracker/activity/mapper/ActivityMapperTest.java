package dev.eugenlinde.activitytracker.activity.mapper;

import dev.eugenlinde.activitytracker.activity.dto.ActivityResponse;
import dev.eugenlinde.activitytracker.activity.model.Activity;
import dev.eugenlinde.activitytracker.activity.model.ActivityType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ActivityMapperTest {

    private final ActivityMapper activityMapper = new ActivityMapper();
    private Activity activity;

    @BeforeEach
    void setUp() {
        activity = new Activity();
        activity.setActivityType(ActivityType.AEROBIC);
        activity.setStartedAt(Instant.ofEpochSecond(1));
        activity.setDuration(Duration.ofSeconds(1));
        activity.setNotes("test");
    }

    @Test
    void mapsEveryFieldToResponse() {
        ActivityResponse activityResponse = activityMapper.mapToActivityResponse(activity);
        assertEquals(ActivityType.AEROBIC, activityResponse.activityType());
        assertEquals(Instant.ofEpochSecond(1), activityResponse.startedAt());
        assertEquals(Duration.ofSeconds(1), activityResponse.duration());
        assertEquals("test", activityResponse.notes());
        assertNull(activityResponse.id());
        assertNull(activityResponse.createdAt());
        assertNull(activityResponse.updatedAt());
    }
}