package dev.eugenlinde.activitytracker.activity.service;

import dev.eugenlinde.activitytracker.activity.ActivityRepository;
import dev.eugenlinde.activitytracker.activity.dto.ActivityRequest;
import dev.eugenlinde.activitytracker.activity.dto.ActivityResponse;
import dev.eugenlinde.activitytracker.activity.mapper.ActivityMapper;
import dev.eugenlinde.activitytracker.activity.model.Activity;
import dev.eugenlinde.activitytracker.activity.model.ActivityType;
import dev.eugenlinde.activitytracker.common.exception.ActivityNotFoundException;
import dev.eugenlinde.activitytracker.user.UserRepository;
import dev.eugenlinde.activitytracker.user.model.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ActivityServiceTest {
    private final Long userId = 1L;
    private final ActivityMapper activityMapper = new ActivityMapper();
    @Captor
    ArgumentCaptor<Activity> activityArgumentCaptor;
    private Long id = 1L;
    private ActivityService activityService;
    private ActivityRepository mockedActivityRepository = mock(ActivityRepository.class);
    private UserRepository mockedUserRepository = mock(UserRepository.class);
    private Activity savedActivity = new Activity();
    private User savedUser = new User();
    private ActivityRequest activityRequest;

    @BeforeEach
    void setUp() {
        activityService = new ActivityService(
                mockedActivityRepository,
                activityMapper,
                mockedUserRepository
        );
        savedActivity.setActivityType(ActivityType.AEROBIC);
        savedActivity.setDuration(Duration.ofSeconds(123));
        savedActivity.setStartedAt(Instant.ofEpochSecond(123));
        savedActivity.setNotes("ActivityServiceTest");
        activityRequest = new ActivityRequest(
                ActivityType.AEROBIC,
                Instant.ofEpochSecond(123),
                Duration.ofSeconds(123),
                "ActivityServiceTest"
        );
    }

    @Test
    void create_returnsActivity() {
        when(mockedActivityRepository.save(any(Activity.class))).thenReturn(savedActivity);
        when(mockedUserRepository.getReferenceById(userId)).thenReturn(savedUser);

        activityService.create(activityRequest, userId);
        verify(mockedActivityRepository).save(activityArgumentCaptor.capture());
        Activity actualActivity = activityArgumentCaptor.getValue();

        assertEquals(activityRequest.activityType(), actualActivity.getActivityType());
        assertEquals(activityRequest.duration(), actualActivity.getDuration());
        assertEquals(activityRequest.startedAt(), actualActivity.getStartedAt());
        assertEquals(activityRequest.notes(), actualActivity.getNotes());
        assertSame(savedUser, actualActivity.getUser());
    }

    @Test
    void findById_whenActivityExists_returnsActivity() {
        when(mockedActivityRepository.findByIdAndUserId(id, userId)).thenReturn(Optional.of(savedActivity));

        ActivityResponse actualActivity = activityService.findById(id, userId);

        assertEquals(savedActivity.getActivityType(), actualActivity.activityType());
        assertEquals(savedActivity.getDuration(), actualActivity.duration());
        assertEquals(savedActivity.getStartedAt(), actualActivity.startedAt());
        assertEquals(savedActivity.getNotes(), actualActivity.notes());
    }

    @Test
    void findById_whenActivityDoesNotExist_throwsActivityNotFoundException() {
        when(mockedActivityRepository.findById(id)).thenReturn(Optional.empty());

        assertThrows(ActivityNotFoundException.class, () -> activityService.findById(id, userId));
    }

    @Test
    void updateById_whenActivityExists_updatesAndReturnsActivity() {
        ActivityRequest updateRequest = new ActivityRequest(
                ActivityType.WEIGHTS,
                Instant.ofEpochSecond(456),
                Duration.ofSeconds(456),
                "update"
        );
        when(mockedActivityRepository.findByIdAndUserId(id, userId))
                .thenReturn(Optional.of(savedActivity));
        when(mockedActivityRepository.saveAndFlush(any(Activity.class)))
                .thenReturn(savedActivity);

        ActivityResponse actualActivity = activityService.updateById(id, updateRequest, userId);

        verify(mockedActivityRepository).saveAndFlush(activityArgumentCaptor.capture());
        Activity capturedActivity = activityArgumentCaptor.getValue();

        assertEquals(updateRequest.activityType(), capturedActivity.getActivityType());
        assertEquals(updateRequest.duration(), capturedActivity.getDuration());
        assertEquals(updateRequest.startedAt(), capturedActivity.getStartedAt());
        assertEquals(updateRequest.notes(), capturedActivity.getNotes());

        assertEquals(updateRequest.activityType(), actualActivity.activityType());
        assertEquals(updateRequest.duration(), actualActivity.duration());
        assertEquals(updateRequest.startedAt(), actualActivity.startedAt());
        assertEquals(updateRequest.notes(), actualActivity.notes());
    }
}