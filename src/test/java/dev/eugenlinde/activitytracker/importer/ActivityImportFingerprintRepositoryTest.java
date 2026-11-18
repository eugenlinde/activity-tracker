package dev.eugenlinde.activitytracker.importer;

import dev.eugenlinde.activitytracker.TestcontainersConfiguration;
import dev.eugenlinde.activitytracker.activity.ActivityRepository;
import dev.eugenlinde.activitytracker.activity.model.Activity;
import dev.eugenlinde.activitytracker.activity.model.ActivityType;
import dev.eugenlinde.activitytracker.user.model.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DataJpaTest
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class ActivityImportFingerprintRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;
    @Autowired
    private ActivityRepository activityRepository;

    private User persistUser(String email) {
        User user = new User();
        user.setEmail(email);
        user.setFirstName("First");
        user.setLastName("Last");
        user.setPassword("hash");
        user.setEnabled(true);
        return entityManager.persist(user);
    }

    private void persistActivity(User user, String fingerprint) {
        Activity activity = new Activity();
        activity.setUser(user);
        activity.setActivityType(ActivityType.WEIGHTS);
        activity.setStartedAt(Instant.parse("2026-11-03T10:00:00Z"));
        activity.setImportFingerprint(fingerprint);
        entityManager.persist(activity);
    }

    @Test
    void countImportFingerprints_countsOnlyTheCallersImportedActivitiesPerFingerprint() {
        User owner = persistUser("import-owner@example.com");
        User stranger = persistUser("import-stranger@example.com");
        persistActivity(owner, "aaa");
        persistActivity(owner, "aaa");
        persistActivity(owner, "bbb");
        persistActivity(owner, null);
        persistActivity(stranger, "aaa");
        entityManager.flush();
        entityManager.clear();

        Map<String, Long> counts = activityRepository.countImportFingerprints(owner.getId()).stream()
                .collect(Collectors.toMap(ActivityRepository.FingerprintCount::getFingerprint,
                        ActivityRepository.FingerprintCount::getTotal));

        assertEquals(Map.of("aaa", 2L, "bbb", 1L), counts);
    }
}
