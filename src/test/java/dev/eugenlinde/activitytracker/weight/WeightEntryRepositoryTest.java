package dev.eugenlinde.activitytracker.weight;

import dev.eugenlinde.activitytracker.TestcontainersConfiguration;
import dev.eugenlinde.activitytracker.user.model.User;
import dev.eugenlinde.activitytracker.weight.model.WeightEntry;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class WeightEntryRepositoryTest {
    @Autowired
    private TestEntityManager entityManager;
    @Autowired
    private WeightEntryRepository weightEntryRepository;

    private User owner;
    private User stranger;

    @BeforeEach
    void setUp() {
        owner = persistUser("weight-owner@example.com");
        stranger = persistUser("weight-stranger@example.com");
    }

    @Test
    void rangeQuery_returnsOnlyThatUsersEntriesInRangeOldestFirst() {
        persistEntry(owner, "2026-11-10", "81.5");
        persistEntry(owner, "2026-11-03", "82.0");
        persistEntry(owner, "2026-08-01", "84.2");
        persistEntry(stranger, "2026-11-05", "70.0");

        List<WeightEntry> entries = weightEntryRepository.findAllByUserIdAndEntryDateBetweenOrderByEntryDateAsc(
                owner.getId(), LocalDate.parse("2026-11-01"), LocalDate.parse("2026-11-30"));

        assertEquals(
                List.of(LocalDate.parse("2026-11-03"), LocalDate.parse("2026-11-10")),
                entries.stream().map(WeightEntry::getEntryDate).toList());
    }

    @Test
    void rangeQuery_includesBothEndsOfTheRange() {
        persistEntry(owner, "2026-11-01", "82.0");
        persistEntry(owner, "2026-11-30", "81.0");

        List<WeightEntry> entries = weightEntryRepository.findAllByUserIdAndEntryDateBetweenOrderByEntryDateAsc(
                owner.getId(), LocalDate.parse("2026-11-01"), LocalDate.parse("2026-11-30"));

        assertEquals(2, entries.size());
    }

    @Test
    void findByUserIdAndEntryDate_whenEntryBelongsToAnotherUser_returnsEmpty() {
        persistEntry(stranger, "2026-11-05", "70.0");

        assertTrue(weightEntryRepository.findByUserIdAndEntryDate(owner.getId(), LocalDate.parse("2026-11-05")).isEmpty());
    }

    @Test
    void findByUserIdAndEntryDate_whenEntryBelongsToUser_returnsItWithOneDecimal() {
        persistEntry(owner, "2026-11-05", "81.3");
        entityManager.clear();

        WeightEntry entry = weightEntryRepository
                .findByUserIdAndEntryDate(owner.getId(), LocalDate.parse("2026-11-05"))
                .orElseThrow();

        assertEquals(0, new BigDecimal("81.3").compareTo(entry.getWeightKg()));
        assertNotNull(entry.getCreatedAt());
        assertNotNull(entry.getUpdatedAt());
    }

    @Test
    void secondEntryForSameUserAndDate_isRejectedByTheDatabase() {
        persistEntry(owner, "2026-11-05", "81.3");

        assertThrows(ConstraintViolationException.class, () -> persistEntry(owner, "2026-11-05", "81.9"));
    }

    @Test
    void sameDateForDifferentUsers_isAllowed() {
        persistEntry(owner, "2026-11-05", "81.3");
        persistEntry(stranger, "2026-11-05", "70.0");

        assertTrue(weightEntryRepository.findByUserIdAndEntryDate(stranger.getId(), LocalDate.parse("2026-11-05")).isPresent());
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

    private WeightEntry persistEntry(User user, String date, String weightKg) {
        WeightEntry entry = new WeightEntry();
        entry.setUser(user);
        entry.setEntryDate(LocalDate.parse(date));
        entry.setWeightKg(new BigDecimal(weightKg));
        return entityManager.persistAndFlush(entry);
    }
}
