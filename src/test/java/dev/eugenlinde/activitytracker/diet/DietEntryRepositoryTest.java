package dev.eugenlinde.activitytracker.diet;

import dev.eugenlinde.activitytracker.TestcontainersConfiguration;
import dev.eugenlinde.activitytracker.diet.model.DietEntry;
import dev.eugenlinde.activitytracker.diet.model.DietStatus;
import dev.eugenlinde.activitytracker.user.model.User;
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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class DietEntryRepositoryTest {
    @Autowired
    private TestEntityManager entityManager;
    @Autowired
    private DietEntryRepository dietEntryRepository;

    private User owner;
    private User stranger;

    @BeforeEach
    void setUp() {
        owner = persistUser("diet-owner@example.com");
        stranger = persistUser("diet-stranger@example.com");
    }

    @Test
    void rangeQuery_returnsOnlyThatUsersEntriesInRangeOldestFirst() {
        persistEntry(owner, "2026-11-10", DietStatus.MORE, null);
        persistEntry(owner, "2026-11-03", DietStatus.ON_PLAN, null);
        persistEntry(owner, "2026-08-01", DietStatus.LESS, null);
        persistEntry(stranger, "2026-11-05", DietStatus.OUTSIDE, null);

        List<DietEntry> entries = dietEntryRepository.findAllByUserIdAndEntryDateBetweenOrderByEntryDateAsc(
                owner.getId(), LocalDate.parse("2026-11-01"), LocalDate.parse("2026-11-30"));

        assertEquals(
                List.of(LocalDate.parse("2026-11-03"), LocalDate.parse("2026-11-10")),
                entries.stream().map(DietEntry::getEntryDate).toList());
    }

    @Test
    void rangeQuery_includesBothEndsOfTheRange() {
        persistEntry(owner, "2026-11-01", DietStatus.ON_PLAN, null);
        persistEntry(owner, "2026-11-30", DietStatus.ON_PLAN, null);

        List<DietEntry> entries = dietEntryRepository.findAllByUserIdAndEntryDateBetweenOrderByEntryDateAsc(
                owner.getId(), LocalDate.parse("2026-11-01"), LocalDate.parse("2026-11-30"));

        assertEquals(2, entries.size());
    }

    @Test
    void findByUserIdAndEntryDate_whenEntryBelongsToAnotherUser_returnsEmpty() {
        persistEntry(stranger, "2026-11-05", DietStatus.MORE, "Cake");

        assertTrue(dietEntryRepository.findByUserIdAndEntryDate(owner.getId(), LocalDate.parse("2026-11-05")).isEmpty());
    }

    @Test
    void secondEntryForSameUserAndDate_isRejectedByTheDatabase() {
        persistEntry(owner, "2026-11-05", DietStatus.MORE, null);

        assertThrows(
                ConstraintViolationException.class,
                () -> persistEntry(owner, "2026-11-05", DietStatus.LESS, null));
    }

    @Test
    void sameDateForDifferentUsers_isAllowed() {
        persistEntry(owner, "2026-11-05", DietStatus.MORE, null);
        persistEntry(stranger, "2026-11-05", DietStatus.MORE, null);

        assertTrue(dietEntryRepository.findByUserIdAndEntryDate(stranger.getId(), LocalDate.parse("2026-11-05")).isPresent());
    }

    @Test
    void statusAndComment_surviveARoundTrip() {
        persistEntry(owner, "2026-11-05", DietStatus.OUTSIDE, "Birthday dinner");
        persistEntry(owner, "2026-11-06", DietStatus.LESS, null);
        entityManager.clear();

        DietEntry withComment = dietEntryRepository
                .findByUserIdAndEntryDate(owner.getId(), LocalDate.parse("2026-11-05"))
                .orElseThrow();
        DietEntry withoutComment = dietEntryRepository
                .findByUserIdAndEntryDate(owner.getId(), LocalDate.parse("2026-11-06"))
                .orElseThrow();

        assertEquals(DietStatus.OUTSIDE, withComment.getStatus());
        assertEquals("Birthday dinner", withComment.getComment());
        assertEquals(DietStatus.LESS, withoutComment.getStatus());
        assertNull(withoutComment.getComment());
        assertNotNull(withComment.getCreatedAt());
        assertNotNull(withComment.getUpdatedAt());
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

    private DietEntry persistEntry(User user, String date, DietStatus status, String comment) {
        DietEntry entry = new DietEntry();
        entry.setUser(user);
        entry.setEntryDate(LocalDate.parse(date));
        entry.setStatus(status);
        entry.setComment(comment);
        return entityManager.persistAndFlush(entry);
    }
}
