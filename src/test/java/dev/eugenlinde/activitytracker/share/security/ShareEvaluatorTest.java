package dev.eugenlinde.activitytracker.share.security;

import dev.eugenlinde.activitytracker.activity.ActivityRepository;
import dev.eugenlinde.activitytracker.activity.model.Activity;
import dev.eugenlinde.activitytracker.media.MediaRepository;
import dev.eugenlinde.activitytracker.media.model.Media;
import dev.eugenlinde.activitytracker.share.ShareRepository;
import dev.eugenlinde.activitytracker.user.model.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ShareEvaluatorTest {
    private final Long ownerId = 1L;
    private final Long viewerId = 2L;
    private final Long strangerId = 3L;
    private final Long activityId = 10L;
    private final Long mediaId = 20L;

    private final ShareRepository mockedShareRepository = mock(ShareRepository.class);
    private final MediaRepository mockedMediaRepository = mock(MediaRepository.class);
    private final ActivityRepository mockedActivityRepository = mock(ActivityRepository.class);
    private ShareEvaluator shareEvaluator;
    private Activity activity;
    private Media media;

    @BeforeEach
    void setUp() {
        shareEvaluator = new ShareEvaluator(mockedShareRepository, mockedMediaRepository, mockedActivityRepository);

        User owner = new User();
        ReflectionTestUtils.setField(owner, "id", ownerId);
        activity = new Activity();
        ReflectionTestUtils.setField(activity, "id", activityId);
        activity.setUser(owner);
        media = new Media();
        ReflectionTestUtils.setField(media, "id", mediaId);
        media.setActivity(activity);

        when(mockedShareRepository.existsByOwnerIdAndViewerId(ownerId, viewerId)).thenReturn(true);
    }

    @Test
    void unsupportedPermission_isDenied() {
        assertFalse(shareEvaluator.hasPermission(as(ownerId), ownerId, "ActivityLog", "WRITE"));
        verifyNoInteractions(mockedShareRepository, mockedMediaRepository, mockedActivityRepository);
    }

    @Test
    void nonStringPermission_isDenied() {
        assertFalse(shareEvaluator.hasPermission(as(ownerId), ownerId, "ActivityLog", 1));
    }

    @Test
    void unknownTargetType_isDenied() {
        assertFalse(shareEvaluator.hasPermission(as(ownerId), ownerId, "Share", "READ"));
    }

    @Test
    void domainObjectOverload_isAlwaysDenied() {
        assertFalse(shareEvaluator.hasPermission(as(ownerId), activity, "READ"));
    }

    @Test
    void activityLog_owner_isAllowedWithoutShareLookup() {
        assertTrue(shareEvaluator.hasPermission(as(ownerId), ownerId, "ActivityLog", "READ"));
        verifyNoInteractions(mockedShareRepository);
    }

    @Test
    void activityLog_sharedViewer_isAllowed() {
        assertTrue(shareEvaluator.hasPermission(as(viewerId), ownerId, "ActivityLog", "READ"));
    }

    @Test
    void activityLog_stranger_isDenied() {
        assertFalse(shareEvaluator.hasPermission(as(strangerId), ownerId, "ActivityLog", "READ"));
    }

    @Test
    void activity_whenMissing_isDenied() {
        when(mockedActivityRepository.findById(activityId)).thenReturn(Optional.empty());

        assertFalse(shareEvaluator.hasPermission(as(ownerId), activityId, "Activity", "READ"));
    }

    @Test
    void activity_owner_isAllowed() {
        when(mockedActivityRepository.findById(activityId)).thenReturn(Optional.of(activity));

        assertTrue(shareEvaluator.hasPermission(as(ownerId), activityId, "Activity", "READ"));
    }

    @Test
    void activity_sharedViewer_isAllowed() {
        when(mockedActivityRepository.findById(activityId)).thenReturn(Optional.of(activity));

        assertTrue(shareEvaluator.hasPermission(as(viewerId), activityId, "Activity", "READ"));
    }

    @Test
    void activity_stranger_isDenied() {
        when(mockedActivityRepository.findById(activityId)).thenReturn(Optional.of(activity));

        assertFalse(shareEvaluator.hasPermission(as(strangerId), activityId, "Activity", "READ"));
    }

    @Test
    void media_whenMissing_isDenied() {
        when(mockedMediaRepository.findById(mediaId)).thenReturn(Optional.empty());

        assertFalse(shareEvaluator.hasPermission(as(ownerId), mediaId, "Media", "READ"));
        assertFalse(shareEvaluator.hasPermission(as(ownerId), mediaId, "Media", "DELETE"));
    }

    @Test
    void mediaRead_owner_isAllowed() {
        when(mockedMediaRepository.findById(mediaId)).thenReturn(Optional.of(media));

        assertTrue(shareEvaluator.hasPermission(as(ownerId), mediaId, "Media", "READ"));
    }

    @Test
    void mediaRead_sharedViewer_isAllowed() {
        when(mockedMediaRepository.findById(mediaId)).thenReturn(Optional.of(media));

        assertTrue(shareEvaluator.hasPermission(as(viewerId), mediaId, "Media", "READ"));
    }

    @Test
    void mediaRead_stranger_isDenied() {
        when(mockedMediaRepository.findById(mediaId)).thenReturn(Optional.of(media));

        assertFalse(shareEvaluator.hasPermission(as(strangerId), mediaId, "Media", "READ"));
    }

    @Test
    void mediaDelete_owner_isAllowed() {
        when(mockedMediaRepository.findById(mediaId)).thenReturn(Optional.of(media));

        assertTrue(shareEvaluator.hasPermission(as(ownerId), mediaId, "Media", "DELETE"));
    }

    @Test
    void mediaDelete_sharedViewer_isDeniedWithoutShareLookup() {
        when(mockedMediaRepository.findById(mediaId)).thenReturn(Optional.of(media));

        assertFalse(shareEvaluator.hasPermission(as(viewerId), mediaId, "Media", "DELETE"));
        verifyNoInteractions(mockedShareRepository);
    }

    @Test
    void mediaDelete_lookupUsesMediaIdNotActivityId() {
        when(mockedMediaRepository.findById(mediaId)).thenReturn(Optional.of(media));

        shareEvaluator.hasPermission(as(ownerId), mediaId, "Media", "DELETE");

        verify(mockedMediaRepository).findById(mediaId);
        verifyNoInteractions(mockedActivityRepository);
    }

    private Authentication as(Long userId) {
        return new TestingAuthenticationToken(userId.toString(), null);
    }
}
