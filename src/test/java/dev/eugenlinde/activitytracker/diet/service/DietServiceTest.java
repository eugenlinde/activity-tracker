package dev.eugenlinde.activitytracker.diet.service;

import dev.eugenlinde.activitytracker.common.EntryDatePolicy;
import dev.eugenlinde.activitytracker.common.exception.DietEntryNotFoundException;
import dev.eugenlinde.activitytracker.common.exception.InvalidDateRangeException;
import dev.eugenlinde.activitytracker.common.exception.InvalidEntryDateException;
import dev.eugenlinde.activitytracker.diet.DietEntryRepository;
import dev.eugenlinde.activitytracker.diet.dto.DietResponse;
import dev.eugenlinde.activitytracker.diet.model.DietEntry;
import dev.eugenlinde.activitytracker.diet.model.DietStatus;
import dev.eugenlinde.activitytracker.user.UserRepository;
import dev.eugenlinde.activitytracker.user.model.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class DietServiceTest {
    private static final LocalDate TODAY = LocalDate.parse("2026-11-27");

    private final Long userId = 7L;
    private final DietEntryRepository dietEntryRepository = mock(DietEntryRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final EntryDatePolicy entryDatePolicy =
            new EntryDatePolicy(Clock.fixed(Instant.parse("2026-11-27T10:00:00Z"), ZoneOffset.UTC));
    private DietService dietService;

    @BeforeEach
    void setUp() {
        dietService = new DietService(dietEntryRepository, userRepository, entryDatePolicy);
        when(dietEntryRepository.saveAndFlush(any(DietEntry.class))).thenAnswer(call -> call.getArgument(0));
    }

    @Test
    void upsert_whenNoEntryForThatDate_createsOneForTheUser() {
        User user = new User();
        when(dietEntryRepository.findByUserIdAndEntryDate(userId, TODAY)).thenReturn(Optional.empty());
        when(userRepository.getReferenceById(userId)).thenReturn(user);

        DietResponse response = dietService.upsert(userId, TODAY, DietStatus.MORE, "Pizza night");

        ArgumentCaptor<DietEntry> saved = ArgumentCaptor.forClass(DietEntry.class);
        verify(dietEntryRepository).saveAndFlush(saved.capture());
        assertSame(user, saved.getValue().getUser());
        assertEquals(TODAY, saved.getValue().getEntryDate());
        assertEquals(DietStatus.MORE, saved.getValue().getStatus());
        assertEquals("Pizza night", saved.getValue().getComment());
        assertEquals(new DietResponse(TODAY, DietStatus.MORE, "Pizza night"), response);
    }

    @Test
    void upsert_whenEntryExists_replacesStatusAndCommentOnTheSameEntry() {
        DietEntry existing = entry(TODAY, DietStatus.ON_PLAN, "Old");
        when(dietEntryRepository.findByUserIdAndEntryDate(userId, TODAY)).thenReturn(Optional.of(existing));

        DietResponse response = dietService.upsert(userId, TODAY, DietStatus.LESS, "New");

        ArgumentCaptor<DietEntry> saved = ArgumentCaptor.forClass(DietEntry.class);
        verify(dietEntryRepository).saveAndFlush(saved.capture());
        assertSame(existing, saved.getValue());
        assertEquals(DietStatus.LESS, response.status());
        assertEquals("New", response.comment());
        verifyNoInteractions(userRepository);
    }

    @Test
    void upsert_withBlankComment_storesNoComment() {
        when(dietEntryRepository.findByUserIdAndEntryDate(userId, TODAY)).thenReturn(Optional.empty());

        DietResponse response = dietService.upsert(userId, TODAY, DietStatus.ON_PLAN, "   ");

        assertNull(response.comment());
    }

    @Test
    void upsert_withEmptyComment_storesNoComment() {
        when(dietEntryRepository.findByUserIdAndEntryDate(userId, TODAY)).thenReturn(Optional.empty());

        DietResponse response = dietService.upsert(userId, TODAY, DietStatus.ON_PLAN, "");

        assertNull(response.comment());
    }

    @Test
    void upsert_trimsTheComment() {
        when(dietEntryRepository.findByUserIdAndEntryDate(userId, TODAY)).thenReturn(Optional.empty());

        DietResponse response = dietService.upsert(userId, TODAY, DietStatus.ON_PLAN, "  Lunch skipped \n");

        assertEquals("Lunch skipped", response.comment());
    }

    @Test
    void upsert_withoutComment_clearsTheExistingComment() {
        DietEntry existing = entry(TODAY, DietStatus.MORE, "Pizza night");
        when(dietEntryRepository.findByUserIdAndEntryDate(userId, TODAY)).thenReturn(Optional.of(existing));

        DietResponse response = dietService.upsert(userId, TODAY, DietStatus.MORE, null);

        assertNull(response.comment());
        assertNull(existing.getComment());
    }

    @Test
    void upsert_whenDateIsFurtherInTheFuture_isRejectedAndNothingIsSaved() {
        assertThrows(
                InvalidEntryDateException.class,
                () -> dietService.upsert(userId, TODAY.plusDays(2), DietStatus.MORE, null));

        verify(dietEntryRepository, never()).saveAndFlush(any());
    }

    @Test
    void upsert_forTomorrow_isAccepted() {
        when(dietEntryRepository.findByUserIdAndEntryDate(userId, TODAY.plusDays(1))).thenReturn(Optional.empty());

        DietResponse response = dietService.upsert(userId, TODAY.plusDays(1), DietStatus.MORE, null);

        assertEquals(TODAY.plusDays(1), response.date());
    }

    @Test
    void delete_whenEntryExists_deletesIt() {
        DietEntry existing = entry(TODAY, DietStatus.MORE, null);
        when(dietEntryRepository.findByUserIdAndEntryDate(userId, TODAY)).thenReturn(Optional.of(existing));

        dietService.delete(userId, TODAY);

        verify(dietEntryRepository).delete(existing);
    }

    @Test
    void delete_whenNoEntry_throwsNotFound() {
        when(dietEntryRepository.findByUserIdAndEntryDate(userId, TODAY)).thenReturn(Optional.empty());

        assertThrows(DietEntryNotFoundException.class, () -> dietService.delete(userId, TODAY));

        verify(dietEntryRepository, never()).delete(any());
    }

    @Test
    void findAll_returnsTheRepositoryEntriesAsResponses() {
        LocalDate from = LocalDate.parse("2026-11-01");
        LocalDate to = LocalDate.parse("2026-11-30");
        when(dietEntryRepository.findAllByUserIdAndEntryDateBetweenOrderByEntryDateAsc(userId, from, to))
                .thenReturn(List.of(entry(LocalDate.parse("2026-11-03"), DietStatus.LESS, "Light")));

        List<DietResponse> responses = dietService.findAll(userId, from, to);

        assertEquals(List.of(new DietResponse(LocalDate.parse("2026-11-03"), DietStatus.LESS, "Light")), responses);
    }

    @Test
    void findAll_whenFromIsAfterTo_throws() {
        InvalidDateRangeException exception = assertThrows(
                InvalidDateRangeException.class,
                () -> dietService.findAll(userId, LocalDate.parse("2026-11-30"), LocalDate.parse("2026-11-01")));

        assertEquals("'from' must not be after 'to'", exception.getMessage());
        verifyNoInteractions(dietEntryRepository);
    }

    @Test
    void findAll_whenRangeIs367Days_throws() {
        LocalDate from = LocalDate.parse("2026-01-01");

        InvalidDateRangeException exception = assertThrows(
                InvalidDateRangeException.class,
                () -> dietService.findAll(userId, from, from.plusDays(366)));

        assertEquals("A range covers at most 366 days", exception.getMessage());
    }

    @Test
    void findAll_whenRangeIs366Days_isAccepted() {
        LocalDate from = LocalDate.parse("2026-01-01");
        LocalDate to = from.plusDays(365);
        when(dietEntryRepository.findAllByUserIdAndEntryDateBetweenOrderByEntryDateAsc(userId, from, to))
                .thenReturn(List.of());

        assertEquals(List.of(), dietService.findAll(userId, from, to));
    }

    private DietEntry entry(LocalDate date, DietStatus status, String comment) {
        DietEntry entry = new DietEntry();
        entry.setEntryDate(date);
        entry.setStatus(status);
        entry.setComment(comment);
        return entry;
    }
}
