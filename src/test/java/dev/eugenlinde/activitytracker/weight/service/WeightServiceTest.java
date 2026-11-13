package dev.eugenlinde.activitytracker.weight.service;

import dev.eugenlinde.activitytracker.common.EntryDatePolicy;
import dev.eugenlinde.activitytracker.common.exception.InvalidEntryDateException;
import dev.eugenlinde.activitytracker.common.exception.WeightEntryNotFoundException;
import dev.eugenlinde.activitytracker.user.UserRepository;
import dev.eugenlinde.activitytracker.user.model.User;
import dev.eugenlinde.activitytracker.weight.WeightEntryRepository;
import dev.eugenlinde.activitytracker.weight.dto.WeightResponse;
import dev.eugenlinde.activitytracker.weight.model.WeightEntry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class WeightServiceTest {
    private static final LocalDate TODAY = LocalDate.parse("2026-11-13");

    private final Long userId = 7L;
    private final WeightEntryRepository weightEntryRepository = mock(WeightEntryRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final EntryDatePolicy entryDatePolicy =
            new EntryDatePolicy(Clock.fixed(Instant.parse("2026-11-13T10:00:00Z"), ZoneOffset.UTC));
    private WeightService weightService;

    @BeforeEach
    void setUp() {
        weightService = new WeightService(weightEntryRepository, userRepository, entryDatePolicy);
        when(weightEntryRepository.saveAndFlush(any(WeightEntry.class))).thenAnswer(call -> call.getArgument(0));
    }

    @Test
    void upsert_whenNoEntryForThatDate_createsOneForTheUser() {
        User user = new User();
        when(weightEntryRepository.findByUserIdAndEntryDate(userId, TODAY)).thenReturn(Optional.empty());
        when(userRepository.getReferenceById(userId)).thenReturn(user);

        WeightResponse response = weightService.upsert(userId, TODAY, new BigDecimal("81.3"));

        ArgumentCaptor<WeightEntry> saved = ArgumentCaptor.forClass(WeightEntry.class);
        verify(weightEntryRepository).saveAndFlush(saved.capture());
        assertSame(user, saved.getValue().getUser());
        assertEquals(TODAY, saved.getValue().getEntryDate());
        assertEquals(new BigDecimal("81.3"), saved.getValue().getWeightKg());
        assertEquals(new WeightResponse(TODAY, new BigDecimal("81.3")), response);
    }

    @Test
    void upsert_whenEntryExists_replacesTheWeightOnTheSameEntry() {
        WeightEntry existing = entry(TODAY, "82.0");
        when(weightEntryRepository.findByUserIdAndEntryDate(userId, TODAY)).thenReturn(Optional.of(existing));

        WeightResponse response = weightService.upsert(userId, TODAY, new BigDecimal("81.3"));

        ArgumentCaptor<WeightEntry> saved = ArgumentCaptor.forClass(WeightEntry.class);
        verify(weightEntryRepository).saveAndFlush(saved.capture());
        assertSame(existing, saved.getValue());
        assertEquals(new BigDecimal("81.3"), response.weightKg());
        verifyNoInteractions(userRepository);
    }

    @Test
    void upsert_whenDateIsTomorrowInUtc_isAcceptedForUsersAheadOfUtc() {
        LocalDate tomorrow = TODAY.plusDays(1);
        when(weightEntryRepository.findByUserIdAndEntryDate(userId, tomorrow)).thenReturn(Optional.empty());

        WeightResponse response = weightService.upsert(userId, tomorrow, new BigDecimal("81.3"));

        assertEquals(tomorrow, response.date());
    }

    @Test
    void upsert_whenDateIsFurtherInTheFuture_isRejectedAndNothingIsSaved() {
        LocalDate dayAfterTomorrow = TODAY.plusDays(2);

        assertThrows(
                InvalidEntryDateException.class,
                () -> weightService.upsert(userId, dayAfterTomorrow, new BigDecimal("81.3")));

        verify(weightEntryRepository, never()).saveAndFlush(any());
    }

    @Test
    void findAll_withoutRange_coversTheLastTwelveWeeks() {
        when(weightEntryRepository.findAllByUserIdAndEntryDateBetweenOrderByEntryDateAsc(
                userId, LocalDate.parse("2026-08-21"), LocalDate.parse("2026-11-14")))
                .thenReturn(List.of(entry(LocalDate.parse("2026-11-03"), "82.0"), entry(TODAY, "81.3")));

        List<WeightResponse> responses = weightService.findAll(userId, null, null);

        assertEquals(
                List.of(
                        new WeightResponse(LocalDate.parse("2026-11-03"), new BigDecimal("82.0")),
                        new WeightResponse(TODAY, new BigDecimal("81.3"))),
                responses);
    }

    @Test
    void findAll_withRange_passesItThrough() {
        LocalDate from = LocalDate.parse("2026-01-01");
        LocalDate to = LocalDate.parse("2026-01-31");
        when(weightEntryRepository.findAllByUserIdAndEntryDateBetweenOrderByEntryDateAsc(userId, from, to))
                .thenReturn(List.of());

        assertEquals(List.of(), weightService.findAll(userId, from, to));

        verify(weightEntryRepository).findAllByUserIdAndEntryDateBetweenOrderByEntryDateAsc(userId, from, to);
    }

    @Test
    void delete_whenEntryExists_deletesIt() {
        WeightEntry existing = entry(TODAY, "82.0");
        when(weightEntryRepository.findByUserIdAndEntryDate(userId, TODAY)).thenReturn(Optional.of(existing));

        weightService.delete(userId, TODAY);

        verify(weightEntryRepository).delete(existing);
    }

    @Test
    void delete_whenNoEntryForThatDate_throwsNotFound() {
        when(weightEntryRepository.findByUserIdAndEntryDate(userId, TODAY)).thenReturn(Optional.empty());

        assertThrows(WeightEntryNotFoundException.class, () -> weightService.delete(userId, TODAY));

        verify(weightEntryRepository, never()).delete(any());
    }

    private WeightEntry entry(LocalDate date, String weightKg) {
        WeightEntry entry = new WeightEntry();
        entry.setEntryDate(date);
        entry.setWeightKg(new BigDecimal(weightKg));
        return entry;
    }
}
