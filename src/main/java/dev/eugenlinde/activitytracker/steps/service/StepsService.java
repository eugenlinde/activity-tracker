package dev.eugenlinde.activitytracker.steps.service;

import dev.eugenlinde.activitytracker.common.EntryDatePolicy;
import dev.eugenlinde.activitytracker.common.exception.InvalidDateRangeException;
import dev.eugenlinde.activitytracker.common.exception.StepEntryNotFoundException;
import dev.eugenlinde.activitytracker.steps.StepEntryRepository;
import dev.eugenlinde.activitytracker.common.exception.UserNotFoundException;
import dev.eugenlinde.activitytracker.steps.dto.StepsGoalResponse;
import dev.eugenlinde.activitytracker.steps.dto.StepsResponse;
import dev.eugenlinde.activitytracker.steps.dto.StepsSummaryResponse;
import dev.eugenlinde.activitytracker.steps.model.StepEntry;
import dev.eugenlinde.activitytracker.steps.model.StepSource;
import dev.eugenlinde.activitytracker.user.UserRepository;
import dev.eugenlinde.activitytracker.user.model.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Service
@Transactional
public class StepsService {
    private static final int DEFAULT_RANGE_WEEKS = 8;
    private static final int MAX_SUMMARY_DAYS = 366;

    private final StepEntryRepository stepEntryRepository;
    private final UserRepository userRepository;
    private final EntryDatePolicy entryDatePolicy;

    public StepsService(
            StepEntryRepository stepEntryRepository,
            UserRepository userRepository,
            EntryDatePolicy entryDatePolicy
    ) {
        this.stepEntryRepository = stepEntryRepository;
        this.userRepository = userRepository;
        this.entryDatePolicy = entryDatePolicy;
    }

    @Transactional(readOnly = true)
    public List<StepsResponse> findAll(Long userId, LocalDate from, LocalDate to) {
        LocalDate rangeEnd = to != null ? to : entryDatePolicy.latestAllowedDate();
        LocalDate rangeStart = from != null ? from : entryDatePolicy.today().minusWeeks(DEFAULT_RANGE_WEEKS);
        return stepEntryRepository
                .findAllByUserIdAndEntryDateBetweenOrderByEntryDateAsc(userId, rangeStart, rangeEnd)
                .stream()
                .map(StepsResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public StepsSummaryResponse summarize(Long userId, LocalDate from, LocalDate to) {
        if (from.isAfter(to)) {
            throw new InvalidDateRangeException("'from' must not be after 'to'");
        }
        if (ChronoUnit.DAYS.between(from, to) + 1 > MAX_SUMMARY_DAYS) {
            throw new InvalidDateRangeException("A summary covers at most 366 days");
        }
        List<StepEntry> entries = stepEntryRepository
                .findAllByUserIdAndEntryDateBetweenOrderByEntryDateAsc(userId, from, to);
        int daysLogged = entries.size();
        if (daysLogged == 0) {
            return new StepsSummaryResponse(from, to, 0, 0, 0, null);
        }
        long totalSteps = entries.stream().mapToLong(StepEntry::getSteps).sum();
        long averagePerLoggedDay = (2 * totalSteps + daysLogged) / (2L * daysLogged);
        StepEntry bestDay = entries.stream()
                .max(Comparator.comparingInt(StepEntry::getSteps)
                        .thenComparing(StepEntry::getEntryDate, Comparator.reverseOrder()))
                .orElseThrow();
        return new StepsSummaryResponse(
                from, to, daysLogged, totalSteps, averagePerLoggedDay, StepsResponse.from(bestDay));
    }

    public StepsResponse upsert(Long userId, LocalDate date, int steps) {
        entryDatePolicy.requireNotInFuture(date);
        StepEntry entry = stepEntryRepository
                .findByUserIdAndEntryDate(userId, date)
                .orElseGet(() -> newEntry(userId, date));
        entry.setSteps(steps);
        entry.setSource(StepSource.MANUAL);
        return StepsResponse.from(stepEntryRepository.saveAndFlush(entry));
    }

    public boolean saveFromGarmin(Long userId, LocalDate date, int steps) {
        Optional<StepEntry> existing = stepEntryRepository.findByUserIdAndEntryDate(userId, date);
        if (existing.isPresent()) {
            StepEntry entry = existing.get();
            if (entry.getSource() != StepSource.GARMIN) {
                return false;
            }
            entry.setSteps(steps);
            stepEntryRepository.saveAndFlush(entry);
            return true;
        }
        StepEntry entry = newEntry(userId, date);
        entry.setSteps(steps);
        entry.setSource(StepSource.GARMIN);
        stepEntryRepository.saveAndFlush(entry);
        return true;
    }

    public void delete(Long userId, LocalDate date) {
        StepEntry entry = stepEntryRepository
                .findByUserIdAndEntryDate(userId, date)
                .orElseThrow(StepEntryNotFoundException::new);
        stepEntryRepository.delete(entry);
    }

    @Transactional(readOnly = true)
    public StepsGoalResponse findGoal(Long userId) {
        User user = userRepository.findById(userId).orElseThrow(UserNotFoundException::new);
        return new StepsGoalResponse(user.getStepsGoal());
    }

    public StepsGoalResponse updateGoal(Long userId, int goal) {
        User user = userRepository.findById(userId).orElseThrow(UserNotFoundException::new);
        user.setStepsGoal(goal);
        userRepository.save(user);
        return new StepsGoalResponse(goal);
    }

    private StepEntry newEntry(Long userId, LocalDate date) {
        StepEntry entry = new StepEntry();
        entry.setUser(userRepository.getReferenceById(userId));
        entry.setEntryDate(date);
        return entry;
    }
}
