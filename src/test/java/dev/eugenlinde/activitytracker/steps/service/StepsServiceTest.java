package dev.eugenlinde.activitytracker.steps.service;

import dev.eugenlinde.activitytracker.common.EntryDatePolicy;
import dev.eugenlinde.activitytracker.common.exception.InvalidEntryDateException;
import dev.eugenlinde.activitytracker.common.exception.StepEntryNotFoundException;
import dev.eugenlinde.activitytracker.steps.StepEntryRepository;
import dev.eugenlinde.activitytracker.steps.dto.StepsResponse;
import dev.eugenlinde.activitytracker.steps.model.StepEntry;
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
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class StepsServiceTest {
    private static final LocalDate TODAY = LocalDate.parse("2026-11-13");

    private final Long userId = 7L;
    private final StepEntryRepository stepEntryRepository = mock(StepEntryRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final EntryDatePolicy entryDatePolicy =
            new EntryDatePolicy(Clock.fixed(Instant.parse("2026-11-13T10:00:00Z"), ZoneOffset.UTC));
    private StepsService stepsService;

    @BeforeEach
    void setUp() {
        stepsService = new StepsService(stepEntryRepository, userRepository, entryDatePolicy);
        when(stepEntryRepository.saveAndFlush(any(StepEntry.class))).thenAnswer(call -> call.getArgument(0));
    }

    @Test
    void upsert_whenNoEntryForThatDate_createsOneForTheUser() {
        User user = new User();
        when(stepEntryRepository.findByUserIdAndEntryDate(userId, TODAY)).thenReturn(Optional.empty());
        when(userRepository.getReferenceById(userId)).thenReturn(user);

        StepsResponse response = stepsService.upsert(userId, TODAY, 9870);

        ArgumentCaptor<StepEntry> saved = ArgumentCaptor.forClass(StepEntry.class);
        verify(stepEntryRepository).saveAndFlush(saved.capture());
        assertSame(user, saved.getValue().getUser());
        assertEquals(TODAY, saved.getValue().getEntryDate());
        assertEquals(9870, saved.getValue().getSteps());
        assertEquals(new StepsResponse(TODAY, 9870), response);
    }

    @Test
    void upsert_whenEntryExists_replacesTheStepsOnTheSameEntry() {
        StepEntry existing = entry(TODAY, 4000);
        when(stepEntryRepository.findByUserIdAndEntryDate(userId, TODAY)).thenReturn(Optional.of(existing));

        StepsResponse response = stepsService.upsert(userId, TODAY, 9870);

        ArgumentCaptor<StepEntry> saved = ArgumentCaptor.forClass(StepEntry.class);
        verify(stepEntryRepository).saveAndFlush(saved.capture());
        assertSame(existing, saved.getValue());
        assertEquals(9870, response.steps());
        verifyNoInteractions(userRepository);
    }

    @Test
    void upsert_whenDateIsFurtherInTheFuture_isRejectedAndNothingIsSaved() {
        assertThrows(
                InvalidEntryDateException.class,
                () -> stepsService.upsert(userId, TODAY.plusDays(2), 9870));

        verify(stepEntryRepository, never()).saveAndFlush(any());
    }

    @Test
    void findAll_withoutRange_coversTheLastEightWeeks() {
        when(stepEntryRepository.findAllByUserIdAndEntryDateBetweenOrderByEntryDateAsc(
                userId, LocalDate.parse("2026-09-18"), LocalDate.parse("2026-11-14")))
                .thenReturn(List.of(entry(LocalDate.parse("2026-11-12"), 8200), entry(TODAY, 9870)));

        List<StepsResponse> responses = stepsService.findAll(userId, null, null);

        assertEquals(
                List.of(new StepsResponse(LocalDate.parse("2026-11-12"), 8200), new StepsResponse(TODAY, 9870)),
                responses);
    }

    @Test
    void findAll_withRange_passesItThrough() {
        LocalDate from = LocalDate.parse("2026-01-01");
        LocalDate to = LocalDate.parse("2026-01-31");
        when(stepEntryRepository.findAllByUserIdAndEntryDateBetweenOrderByEntryDateAsc(userId, from, to))
                .thenReturn(List.of());

        assertEquals(List.of(), stepsService.findAll(userId, from, to));

        verify(stepEntryRepository).findAllByUserIdAndEntryDateBetweenOrderByEntryDateAsc(userId, from, to);
    }

    @Test
    void delete_whenEntryExists_deletesIt() {
        StepEntry existing = entry(TODAY, 4000);
        when(stepEntryRepository.findByUserIdAndEntryDate(userId, TODAY)).thenReturn(Optional.of(existing));

        stepsService.delete(userId, TODAY);

        verify(stepEntryRepository).delete(existing);
    }

    @Test
    void delete_whenNoEntryForThatDate_throwsNotFound() {
        when(stepEntryRepository.findByUserIdAndEntryDate(userId, TODAY)).thenReturn(Optional.empty());

        assertThrows(StepEntryNotFoundException.class, () -> stepsService.delete(userId, TODAY));

        verify(stepEntryRepository, never()).delete(any());
    }

    private StepEntry entry(LocalDate date, int steps) {
        StepEntry entry = new StepEntry();
        entry.setEntryDate(date);
        entry.setSteps(steps);
        return entry;
    }
}
