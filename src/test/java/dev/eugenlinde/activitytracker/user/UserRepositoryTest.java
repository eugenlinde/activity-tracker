package dev.eugenlinde.activitytracker.user;

import dev.eugenlinde.activitytracker.TestcontainersConfiguration;
import dev.eugenlinde.activitytracker.user.model.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DataJpaTest
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class UserRepositoryTest {
    @Autowired
    private TestEntityManager entityManager;

    @Test
    void stepsGoal_whenNeverTouched_readsBackAsTheDefault() {
        User user = persistUser("goal-default@example.com");
        entityManager.flush();
        entityManager.clear();

        assertEquals(8000, entityManager.find(User.class, user.getId()).getStepsGoal());
    }

    @Test
    void stepsGoal_whenChanged_isPersisted() {
        User user = persistUser("goal-changed@example.com");
        user.setStepsGoal(12000);
        entityManager.flush();
        entityManager.clear();

        assertEquals(12000, entityManager.find(User.class, user.getId()).getStepsGoal());
    }

    @Test
    void stepsGoal_changingOneUser_leavesAnotherUnchanged() {
        User changed = persistUser("goal-one@example.com");
        User untouched = persistUser("goal-two@example.com");
        changed.setStepsGoal(15000);
        entityManager.flush();
        entityManager.clear();

        assertEquals(15000, entityManager.find(User.class, changed.getId()).getStepsGoal());
        assertEquals(8000, entityManager.find(User.class, untouched.getId()).getStepsGoal());
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
}
