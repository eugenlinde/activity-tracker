package dev.eugenlinde.activitytracker.garmin.service;

import dev.eugenlinde.activitytracker.common.exception.GarminKeyExistsException;
import dev.eugenlinde.activitytracker.common.exception.GarminKeyNotFoundException;
import dev.eugenlinde.activitytracker.garmin.GarminConnectionRepository;
import dev.eugenlinde.activitytracker.garmin.dto.GarminStatusResponse;
import dev.eugenlinde.activitytracker.garmin.model.GarminConnection;
import dev.eugenlinde.activitytracker.garmin.model.GarminSyncStatus;
import dev.eugenlinde.activitytracker.user.UserRepository;
import dev.eugenlinde.activitytracker.user.model.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GarminConnectionServiceTest {
    private final Long userId = 7L;
    private final GarminConnectionRepository garminConnectionRepository = mock(GarminConnectionRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private GarminConnectionService service;

    @BeforeEach
    void setUp() {
        service = new GarminConnectionService(garminConnectionRepository, userRepository);
        when(garminConnectionRepository.saveAndFlush(any(GarminConnection.class)))
                .thenAnswer(call -> call.getArgument(0));
    }

    @Test
    void status_withoutAKey_isNotConnected() {
        when(garminConnectionRepository.findByUserId(userId)).thenReturn(Optional.empty());

        GarminStatusResponse status = service.status(userId);

        assertFalse(status.connected());
        assertNull(status.connectedAt());
        assertNull(status.lastSyncAt());
        assertNull(status.lastSyncStatus());
    }

    @Test
    void status_withAKey_isConnectedWithTheLastSync() {
        GarminConnection connection = new GarminConnection();
        Instant at = Instant.parse("2026-11-28T10:00:00Z");
        connection.setLastSyncAt(at);
        connection.setLastSyncStatus(GarminSyncStatus.OK);
        when(garminConnectionRepository.findByUserId(userId)).thenReturn(Optional.of(connection));

        GarminStatusResponse status = service.status(userId);

        assertTrue(status.connected());
        assertEquals(at, status.lastSyncAt());
        assertEquals(GarminSyncStatus.OK, status.lastSyncStatus());
    }

    @Test
    void add_trimsTheKeyAndStoresItForTheUser() {
        User user = new User();
        when(garminConnectionRepository.existsByUserId(userId)).thenReturn(false);
        when(userRepository.getReferenceById(userId)).thenReturn(user);

        GarminStatusResponse status = service.add(userId, "  secret-key \n");

        ArgumentCaptor<GarminConnection> saved = ArgumentCaptor.forClass(GarminConnection.class);
        verify(garminConnectionRepository).saveAndFlush(saved.capture());
        assertEquals("secret-key", saved.getValue().getApiKey());
        assertSame(user, saved.getValue().getUser());
        assertTrue(status.connected());
        assertNull(status.lastSyncAt());
        assertNull(status.lastSyncStatus());
    }

    @Test
    void add_whenAKeyExists_throwsAndSavesNothing() {
        when(garminConnectionRepository.existsByUserId(userId)).thenReturn(true);

        assertThrows(GarminKeyExistsException.class, () -> service.add(userId, "secret-key"));

        verify(garminConnectionRepository, never()).saveAndFlush(any());
    }

    @Test
    void remove_deletesTheUsersConnection() {
        GarminConnection connection = new GarminConnection();
        when(garminConnectionRepository.findByUserId(userId)).thenReturn(Optional.of(connection));

        service.remove(userId);

        verify(garminConnectionRepository).delete(connection);
    }

    @Test
    void remove_withoutAKey_throwsNotFound() {
        when(garminConnectionRepository.findByUserId(userId)).thenReturn(Optional.empty());

        assertThrows(GarminKeyNotFoundException.class, () -> service.remove(userId));

        verify(garminConnectionRepository, never()).delete(any());
    }

    @Test
    void recordSync_setsStatusAndTime() {
        GarminConnection connection = new GarminConnection();
        Instant at = Instant.parse("2026-11-28T11:00:00Z");
        when(garminConnectionRepository.findByUserId(userId)).thenReturn(Optional.of(connection));

        service.recordSync(userId, GarminSyncStatus.FAILED, at);

        assertEquals(GarminSyncStatus.FAILED, connection.getLastSyncStatus());
        assertEquals(at, connection.getLastSyncAt());
        verify(garminConnectionRepository).save(connection);
    }

    @Test
    void recordSync_whenTheConnectionIsGone_doesNothing() {
        when(garminConnectionRepository.findByUserId(userId)).thenReturn(Optional.empty());

        service.recordSync(userId, GarminSyncStatus.OK, Instant.parse("2026-11-28T11:00:00Z"));

        verify(garminConnectionRepository, never()).save(any());
    }
}
