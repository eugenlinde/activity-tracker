package dev.eugenlinde.activitytracker.share.service;

import dev.eugenlinde.activitytracker.common.exception.SelfShareException;
import dev.eugenlinde.activitytracker.common.exception.ShareExistsException;
import dev.eugenlinde.activitytracker.common.exception.ShareNotFoundException;
import dev.eugenlinde.activitytracker.common.exception.UserNotFoundException;
import dev.eugenlinde.activitytracker.share.ShareRepository;
import dev.eugenlinde.activitytracker.share.dto.ShareResponse;
import dev.eugenlinde.activitytracker.share.model.Share;
import dev.eugenlinde.activitytracker.user.UserRepository;
import dev.eugenlinde.activitytracker.user.model.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ShareServiceTest {
    private final Long ownerId = 1L;
    private final Long viewerId = 2L;
    private final Long shareId = 10L;
    private final String viewerEmail = "viewer@example.com";
    private final String ownerEmail = "owner@example.com";

    private final ShareRepository mockedShareRepository = mock(ShareRepository.class);
    private final UserRepository mockedUserRepository = mock(UserRepository.class);
    private ShareService shareService;
    private User owner;
    private User viewer;
    private Share share;

    @BeforeEach
    void setUp() {
        shareService = new ShareService(mockedShareRepository, mockedUserRepository);
        owner = user(ownerId, ownerEmail);
        viewer = user(viewerId, viewerEmail);
        share = new Share();
        ReflectionTestUtils.setField(share, "id", shareId);
        share.setOwner(owner);
        share.setViewer(viewer);
    }

    @Test
    void create_whenViewerExists_savesShareFromOwnerToViewer() {
        when(mockedUserRepository.findByEmail(viewerEmail)).thenReturn(Optional.of(viewer));
        when(mockedUserRepository.getReferenceById(ownerId)).thenReturn(owner);
        ArgumentCaptor<Share> captor = ArgumentCaptor.forClass(Share.class);

        shareService.create(ownerId, viewerEmail);

        verify(mockedShareRepository).save(captor.capture());
        assertSame(owner, captor.getValue().getOwner());
        assertSame(viewer, captor.getValue().getViewer());
    }

    @Test
    void create_whenViewerUnknown_throwsUserNotFoundAndSavesNothing() {
        when(mockedUserRepository.findByEmail(viewerEmail)).thenReturn(Optional.empty());

        assertThrows(UserNotFoundException.class, () -> shareService.create(ownerId, viewerEmail));
        verify(mockedShareRepository, never()).save(any());
    }

    @Test
    void create_whenViewerIsOwner_throwsSelfShareAndSavesNothing() {
        when(mockedUserRepository.findByEmail(ownerEmail)).thenReturn(Optional.of(owner));

        assertThrows(SelfShareException.class, () -> shareService.create(ownerId, ownerEmail));
        verify(mockedShareRepository, never()).save(any());
    }

    @Test
    void create_whenShareAlreadyActive_throwsShareExistsAndSavesNothing() {
        when(mockedUserRepository.findByEmail(viewerEmail)).thenReturn(Optional.of(viewer));
        when(mockedShareRepository.existsByOwnerIdAndViewerId(ownerId, viewerId)).thenReturn(true);

        assertThrows(ShareExistsException.class, () -> shareService.create(ownerId, viewerEmail));
        verify(mockedShareRepository, never()).save(any());
    }

    @Test
    void getUserShares_mapsViewerEmail() {
        when(mockedShareRepository.findAllByOwnerId(ownerId)).thenReturn(List.of(share));

        assertEquals(List.of(new ShareResponse(shareId, ownerId, viewerEmail)), shareService.getUserShares(ownerId));
    }

    @Test
    void getUserReceivedShares_mapsOwnerEmail() {
        when(mockedShareRepository.findAllByViewerId(viewerId)).thenReturn(List.of(share));

        assertEquals(List.of(new ShareResponse(shareId, ownerId, ownerEmail)), shareService.getUserReceivedShares(viewerId));
    }

    @Test
    void deleteById_whenOwned_deletesShare() {
        when(mockedShareRepository.findByIdAndOwnerId(shareId, ownerId)).thenReturn(Optional.of(share));

        shareService.deleteById(shareId, ownerId);

        verify(mockedShareRepository).delete(share);
    }

    @Test
    void deleteById_whenNotOwned_throwsShareNotFoundAndDeletesNothing() {
        when(mockedShareRepository.findByIdAndOwnerId(shareId, viewerId)).thenReturn(Optional.empty());

        assertThrows(ShareNotFoundException.class, () -> shareService.deleteById(shareId, viewerId));
        verify(mockedShareRepository, never()).delete(any());
    }

    private User user(Long id, String email) {
        User user = new User();
        ReflectionTestUtils.setField(user, "id", id);
        user.setEmail(email);
        return user;
    }
}
