package dev.eugenlinde.activitytracker.steps.service;

import dev.eugenlinde.activitytracker.common.EntryDatePolicy;
import dev.eugenlinde.activitytracker.common.exception.InvalidDateRangeException;
import dev.eugenlinde.activitytracker.common.exception.InvalidEntryDateException;
import dev.eugenlinde.activitytracker.common.exception.StepEntryNotFoundException;
import dev.eugenlinde.activitytracker.steps.StepEntryRepository;
import dev.eugenlinde.activitytracker.common.exception.UserNotFoundException;
import dev.eugenlinde.activitytracker.steps.dto.StepsGoalResponse;
import dev.eugenlinde.activitytracker.steps.dto.StepsResponse;
import dev.eugenlinde.activitytracker.steps.dto.StepsSummaryResponse;
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
    private static final LocalDate SUMMARY_FROM = LocalDate.parse("2026-11-10");
    private static final LocalDate SUMMARY_TO = LocalDate.parse("2026-11-12");

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

    @Test
    void summarize_whenNothingIsLogged_isZeroWithNoBestDay() {
        when(stepEntryRepository.findAllByUserIdAndEntryDateBetweenOrderByEntryDateAsc(userId, SUMMARY_FROM, SUMMARY_TO))
                .thenReturn(List.of());

        assertEquals(
                new StepsSummaryResponse(SUMMARY_FROM, SUMMARY_TO, 0, 0, 0, null),
                stepsService.summarize(userId, SUMMARY_FROM, SUMMARY_TO));
    }

    @Test
    void summarize_countsTheDaysTotalAverageAndBestDay() {
        when(stepEntryRepository.findAllByUserIdAndEntryDateBetweenOrderByEntryDateAsc(userId, SUMMARY_FROM, SUMMARY_TO))
                .thenReturn(List.of(
                        entry(SUMMARY_FROM, 8000),
                        entry(SUMMARY_FROM.plusDays(1), 10000),
                        entry(SUMMARY_TO, 9001)));

        assertEquals(
                new StepsSummaryResponse(
                        SUMMARY_FROM, SUMMARY_TO, 3, 27001, 9000, new StepsResponse(SUMMARY_FROM.plusDays(1), 10000)),
                stepsService.summarize(userId, SUMMARY_FROM, SUMMARY_TO));
    }

    @Test
    void summarize_roundsTheAverageHalfUp() {
        when(stepEntryRepository.findAllByUserIdAndEntryDateBetweenOrderByEntryDateAsc(userId, SUMMARY_FROM, SUMMARY_TO))
                .thenReturn(List.of(entry(SUMMARY_FROM, 5000), entry(SUMMARY_TO, 5001)));

        assertEquals(5001, stepsService.summarize(userId, SUMMARY_FROM, SUMMARY_TO).averagePerLoggedDay());
    }

    @Test
    void summarize_roundsTheAverageDownBelowHalf() {
        when(stepEntryRepository.findAllByUserIdAndEntryDateBetweenOrderByEntryDateAsc(userId, SUMMARY_FROM, SUMMARY_TO))
                .thenReturn(List.of(entry(SUMMARY_FROM, 1), entry(SUMMARY_FROM.plusDays(1), 1), entry(SUMMARY_TO, 2)));

        assertEquals(1, stepsService.summarize(userId, SUMMARY_FROM, SUMMARY_TO).averagePerLoggedDay());
    }

    @Test
    void summarize_onATieForTheBestDay_picksTheEarlierDate() {
        when(stepEntryRepository.findAllByUserIdAndEntryDateBetweenOrderByEntryDateAsc(userId, SUMMARY_FROM, SUMMARY_TO))
                .thenReturn(List.of(
                        entry(SUMMARY_FROM, 10000),
                        entry(SUMMARY_FROM.plusDays(1), 5000),
                        entry(SUMMARY_TO, 10000)));

        assertEquals(
                new StepsResponse(SUMMARY_FROM, 10000),
                stepsService.summarize(userId, SUMMARY_FROM, SUMMARY_TO).bestDay());
    }

    @Test
    void summarize_keepsATotalAboveIntegerMaxValue() {
        when(stepEntryRepository.findAllByUserIdAndEntryDateBetweenOrderByEntryDateAsc(userId, SUMMARY_FROM, SUMMARY_TO))
                .thenReturn(List.of(entry(SUMMARY_FROM, Integer.MAX_VALUE), entry(SUMMARY_TO, Integer.MAX_VALUE)));

        StepsSummaryResponse summary = stepsService.summarize(userId, SUMMARY_FROM, SUMMARY_TO);

        assertEquals(4294967294L, summary.totalSteps());
        assertEquals(2147483647L, summary.averagePerLoggedDay());
        assertEquals(new StepsResponse(SUMMARY_FROM, Integer.MAX_VALUE), summary.bestDay());
    }

    @Test
    void summarize_whenFromIsAfterTo_isRejectedWithoutQuerying() {
        assertThrows(
                InvalidDateRangeException.class,
                () -> stepsService.summarize(userId, SUMMARY_TO, SUMMARY_FROM));

        verify(stepEntryRepository, never())
                .findAllByUserIdAndEntryDateBetweenOrderByEntryDateAsc(any(), any(), any());
    }

    @Test
    void summarize_rejectsARangeOf367Days() {
        LocalDate to = SUMMARY_FROM.plusDays(366);

        assertThrows(InvalidDateRangeException.class, () -> stepsService.summarize(userId, SUMMARY_FROM, to));

        verify(stepEntryRepository, never())
                .findAllByUserIdAndEntryDateBetweenOrderByEntryDateAsc(any(), any(), any());
    }

    @Test
    void summarize_acceptsARangeOf366Days() {
        LocalDate to = SUMMARY_FROM.plusDays(365);
        when(stepEntryRepository.findAllByUserIdAndEntryDateBetweenOrderByEntryDateAsc(userId, SUMMARY_FROM, to))
                .thenReturn(List.of());

        assertEquals(
                new StepsSummaryResponse(SUMMARY_FROM, to, 0, 0, 0, null),
                stepsService.summarize(userId, SUMMARY_FROM, to));
    }

    @Test
    void findGoal_returnsTheUsersGoal() {
        User user = new User();
        user.setStepsGoal(12000);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        assertEquals(new StepsGoalResponse(12000), stepsService.findGoal(userId));
    }

    @Test
    void findGoal_forAUserWhoNeverChangedIt_returnsTheDefault() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(new User()));

        assertEquals(new StepsGoalResponse(8000), stepsService.findGoal(userId));
    }

    @Test
    void updateGoal_setsTheGoalOnTheLoadedUserAndSavesIt() {
        User user = new User();
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        StepsGoalResponse response = stepsService.updateGoal(userId, 10000);

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(saved.capture());
        assertSame(user, saved.getValue());
        assertEquals(10000, user.getStepsGoal());
        assertEquals(new StepsGoalResponse(10000), response);
    }

    @Test
    void findGoal_whenUserIsMissing_throwsNotFound() {
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThrows(UserNotFoundException.class, () -> stepsService.findGoal(userId));
    }

    @Test
    void updateGoal_whenUserIsMissing_throwsNotFoundAndSavesNothing() {
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThrows(UserNotFoundException.class, () -> stepsService.updateGoal(userId, 10000));

        verify(userRepository, never()).save(any());
    }

    private StepEntry entry(LocalDate date, int steps) {
        StepEntry entry = new StepEntry();
        entry.setEntryDate(date);
        entry.setSteps(steps);
        return entry;
    }
}
