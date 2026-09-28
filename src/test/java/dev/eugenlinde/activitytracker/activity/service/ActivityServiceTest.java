package dev.eugenlinde.activitytracker.activity.service;

import dev.eugenlinde.activitytracker.activity.ActivityRepository;
import dev.eugenlinde.activitytracker.activity.dto.ActivityRequest;
import dev.eugenlinde.activitytracker.activity.dto.ActivityResponse;
import dev.eugenlinde.activitytracker.activity.mapper.ActivityMapper;
import dev.eugenlinde.activitytracker.activity.model.Activity;
import dev.eugenlinde.activitytracker.activity.model.ActivityType;
import dev.eugenlinde.activitytracker.common.exception.ActivityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ActivityServiceTest {
    private final ActivityMapper activityMapper = new ActivityMapper();
    @Captor
    ArgumentCaptor<Activity> activityArgumentCaptor;
    private ActivityService activityService;
    private ActivityRepository mockedActivityRepository = mock(ActivityRepository.class);
    private Activity savedActivity = new Activity();
    private ActivityRequest activityRequest;

    @BeforeEach
    void setUp() {
        this.activityService = new ActivityService(mockedActivityRepository, activityMapper);
        this.savedActivity.setActivityType(ActivityType.AEROBIC);
        this.savedActivity.setDuration(Duration.ofSeconds(123));
        this.savedActivity.setStartedAt(Instant.ofEpochSecond(123));
        this.savedActivity.setNotes("ActivityServiceTest");
        this.activityRequest = new ActivityRequest(
                ActivityType.AEROBIC,
                Instant.ofEpochSecond(123),
                Duration.ofSeconds(123),
                "ActivityServiceTest"
        );
    }

    @Test
    void create_returnsActivity() {
        when(mockedActivityRepository.save(any(Activity.class))).thenReturn(this.savedActivity);

        this.activityService.create(activityRequest);
        verify(mockedActivityRepository).save(activityArgumentCaptor.capture());
        Activity actualActivity = activityArgumentCaptor.getValue();

        assertEquals(activityRequest.activityType(), actualActivity.getActivityType());
        assertEquals(activityRequest.duration(), actualActivity.getDuration());
        assertEquals(activityRequest.startedAt(), actualActivity.getStartedAt());
        assertEquals(activityRequest.notes(), actualActivity.getNotes());
    }

    @Test
    void findById_whenActivityExists_returnsActivity() {
        Long id = 1L;
        when(mockedActivityRepository.findById(id)).thenReturn(Optional.of(savedActivity));

        ActivityResponse actualActivity = activityService.findById(id);

        assertEquals(savedActivity.getActivityType(), actualActivity.activityType());
        assertEquals(savedActivity.getDuration(), actualActivity.duration());
        assertEquals(savedActivity.getStartedAt(), actualActivity.startedAt());
        assertEquals(savedActivity.getNotes(), actualActivity.notes());
    }

    @Test
    void findById_whenActivityDoesNotExist_throwsActivityNotFoundException() {
        Long id = 99L;
        when(mockedActivityRepository.findById(id)).thenReturn(Optional.empty());

        assertThrows(ActivityNotFoundException.class, () -> this.activityService.findById(id));
    }
}