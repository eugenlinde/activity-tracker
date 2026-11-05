package dev.eugenlinde.activitytracker.repository;

import dev.eugenlinde.activitytracker.TestcontainersConfiguration;
import dev.eugenlinde.activitytracker.activity.ActivityRepository;
import dev.eugenlinde.activitytracker.activity.model.Activity;
import dev.eugenlinde.activitytracker.activity.model.ActivityType;
import dev.eugenlinde.activitytracker.activity.model.Session;
import dev.eugenlinde.activitytracker.user.model.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;

import static dev.eugenlinde.activitytracker.activity.ActivityDetailsFixtures.session;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class ActivityDetailsPersistenceTest {
    @Autowired
    private TestEntityManager entityManager;
    @Autowired
    private ActivityRepository activityRepository;

    private User owner;

    @BeforeEach
    void setUp() {
        User user = new User();
        user.setEmail("owner@example.com");
        user.setFirstName("First");
        user.setLastName("Last");
        user.setPassword("hash");
        user.setEnabled(true);
        owner = entityManager.persist(user);
    }

    @Test
    void details_roundTripThroughJsonb() {
        Session details = session();
        Long id = persistActivity(details).getId();
        entityManager.flush();
        entityManager.clear();

        Activity reloaded = activityRepository.findById(id).orElseThrow();

        assertEquals(details, reloaded.getDetails());
    }

    @Test
    void details_areStoredAsJsonObject() {
        Long id = persistActivity(session()).getId();
        entityManager.flush();

        Object type = entityManager.getEntityManager()
                .createNativeQuery("select jsonb_typeof(details) from activities where id = :id")
                .setParameter("id", id)
                .getSingleResult();

        assertEquals("object", type);
    }

    @Test
    void nullDetails_isStoredAsSqlNullNotJsonNull() {
        Long id = persistActivity(null).getId();
        entityManager.flush();

        Object isSqlNull = entityManager.getEntityManager()
                .createNativeQuery("select details is null from activities where id = :id")
                .setParameter("id", id)
                .getSingleResult();

        assertTrue((Boolean) isSqlNull);
    }

    @Test
    void nullDuration_isPersisted() {
        Long id = persistActivity(null).getId();
        entityManager.flush();
        entityManager.clear();

        assertNull(activityRepository.findById(id).orElseThrow().getDuration());
    }

    private Activity persistActivity(Session details) {
        Activity activity = new Activity();
        activity.setActivityType(ActivityType.WEIGHTS);
        activity.setStartedAt(Instant.parse("2026-10-01T08:00:00Z"));
        activity.setDetails(details);
        activity.setUser(owner);
        return entityManager.persist(activity);
    }
}
