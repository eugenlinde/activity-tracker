package dev.eugenlinde.activitytracker.garmin;

import dev.eugenlinde.activitytracker.TestcontainersConfiguration;
import dev.eugenlinde.activitytracker.garmin.model.GarminConnection;
import dev.eugenlinde.activitytracker.garmin.model.GarminSyncStatus;
import dev.eugenlinde.activitytracker.user.model.User;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class GarminConnectionRepositoryTest {
    @Autowired
    private TestEntityManager entityManager;
    @Autowired
    private GarminConnectionRepository garminConnectionRepository;

    private User owner;
    private User stranger;

    @BeforeEach
    void setUp() {
        owner = persistUser("garmin-owner@example.com");
        stranger = persistUser("garmin-stranger@example.com");
    }

    @Test
    void save_thenFindByUserId_returnsTheConnectionWithItsTimestamp() {
        persistConnection(owner, "key-1");
        entityManager.clear();

        GarminConnection found = garminConnectionRepository.findByUserId(owner.getId()).orElseThrow();

        assertEquals("key-1", found.getApiKey());
        assertNotNull(found.getCreatedAt());
        assertNull(found.getLastSyncAt());
        assertNull(found.getLastSyncStatus());
    }

    @Test
    void lastSync_isStored() {
        GarminConnection connection = persistConnection(owner, "key-1");
        Instant at = Instant.parse("2026-11-28T10:00:00Z");
        connection.setLastSyncAt(at);
        connection.setLastSyncStatus(GarminSyncStatus.FAILED);
        entityManager.flush();
        entityManager.clear();

        GarminConnection found = garminConnectionRepository.findByUserId(owner.getId()).orElseThrow();

        assertEquals(GarminSyncStatus.FAILED, found.getLastSyncStatus());
        assertEquals(at, found.getLastSyncAt());
    }

    @Test
    void findByUserId_whenOnlyAnotherUserHasAKey_returnsEmpty() {
        persistConnection(stranger, "key-2");

        assertTrue(garminConnectionRepository.findByUserId(owner.getId()).isEmpty());
        assertFalse(garminConnectionRepository.existsByUserId(owner.getId()));
        assertTrue(garminConnectionRepository.existsByUserId(stranger.getId()));
    }

    @Test
    void findAllUserIds_returnsTheUsersWithAKey() {
        persistConnection(owner, "key-1");
        persistConnection(stranger, "key-2");

        assertEquals(java.util.List.of(owner.getId(), stranger.getId()), garminConnectionRepository.findAllUserIds());
    }

    @Test
    void secondConnectionForTheSameUser_isRejectedByTheDatabase() {
        persistConnection(owner, "key-1");

        assertThrows(ConstraintViolationException.class, () -> persistConnection(owner, "key-2"));
    }

    private User persistUser(String email) {
        User user = new User();
        user.setEmail(email);
        user.setFirstName("First");
        user.setLastName("Last");
        user.setPassword("hash");
        user.setEnabled(true);
        return entityManager.persist(user);
    }

    private GarminConnection persistConnection(User user, String key) {
        GarminConnection connection = new GarminConnection();
        connection.setUser(user);
        connection.setApiKey(key);
        return entityManager.persistAndFlush(connection);
    }
}
