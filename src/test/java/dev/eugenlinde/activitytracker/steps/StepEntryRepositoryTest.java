package dev.eugenlinde.activitytracker.steps;

import dev.eugenlinde.activitytracker.TestcontainersConfiguration;
import dev.eugenlinde.activitytracker.user.model.User;
import dev.eugenlinde.activitytracker.steps.model.StepEntry;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class StepEntryRepositoryTest {
    @Autowired
    private TestEntityManager entityManager;
    @Autowired
    private StepEntryRepository stepEntryRepository;

    private User owner;
    private User stranger;

    @BeforeEach
    void setUp() {
        owner = persistUser("steps-owner@example.com");
        stranger = persistUser("steps-stranger@example.com");
    }

    @Test
    void rangeQuery_returnsOnlyThatUsersEntriesInRangeOldestFirst() {
        persistEntry(owner, "2026-11-10", 9100);
        persistEntry(owner, "2026-11-03", 8200);
        persistEntry(owner, "2026-08-01", 4000);
        persistEntry(stranger, "2026-11-05", 7000);

        List<StepEntry> entries = stepEntryRepository.findAllByUserIdAndEntryDateBetweenOrderByEntryDateAsc(
                owner.getId(), LocalDate.parse("2026-11-01"), LocalDate.parse("2026-11-30"));

        assertEquals(
                List.of(LocalDate.parse("2026-11-03"), LocalDate.parse("2026-11-10")),
                entries.stream().map(StepEntry::getEntryDate).toList());
    }

    @Test
    void rangeQuery_includesBothEndsOfTheRange() {
        persistEntry(owner, "2026-11-01", 8000);
        persistEntry(owner, "2026-11-30", 9000);

        List<StepEntry> entries = stepEntryRepository.findAllByUserIdAndEntryDateBetweenOrderByEntryDateAsc(
                owner.getId(), LocalDate.parse("2026-11-01"), LocalDate.parse("2026-11-30"));

        assertEquals(2, entries.size());
    }

    @Test
    void findByUserIdAndEntryDate_whenEntryBelongsToAnotherUser_returnsEmpty() {
        persistEntry(stranger, "2026-11-05", 7000);

        assertTrue(stepEntryRepository.findByUserIdAndEntryDate(owner.getId(), LocalDate.parse("2026-11-05")).isEmpty());
    }

    @Test
    void findByUserIdAndEntryDate_whenEntryBelongsToUser_returnsIt() {
        persistEntry(owner, "2026-11-05", 7000);
        entityManager.clear();

        StepEntry entry = stepEntryRepository
                .findByUserIdAndEntryDate(owner.getId(), LocalDate.parse("2026-11-05"))
                .orElseThrow();

        assertEquals(7000, entry.getSteps());
        assertNotNull(entry.getCreatedAt());
        assertNotNull(entry.getUpdatedAt());
    }

    @Test
    void secondEntryForSameUserAndDate_isRejectedByTheDatabase() {
        persistEntry(owner, "2026-11-05", 7000);

        assertThrows(ConstraintViolationException.class, () -> persistEntry(owner, "2026-11-05", 7000));
    }

    @Test
    void sameDateForDifferentUsers_isAllowed() {
        persistEntry(owner, "2026-11-05", 7000);
        persistEntry(stranger, "2026-11-05", 7000);

        assertTrue(stepEntryRepository.findByUserIdAndEntryDate(stranger.getId(), LocalDate.parse("2026-11-05")).isPresent());
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

    private StepEntry persistEntry(User user, String date, int steps) {
        StepEntry entry = new StepEntry();
        entry.setUser(user);
        entry.setEntryDate(LocalDate.parse(date));
        entry.setSteps(steps);
        return entityManager.persistAndFlush(entry);
    }
}
