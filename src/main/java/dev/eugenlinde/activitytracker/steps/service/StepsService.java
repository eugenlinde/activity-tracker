package dev.eugenlinde.activitytracker.steps.service;

import dev.eugenlinde.activitytracker.common.EntryDatePolicy;
import dev.eugenlinde.activitytracker.common.exception.StepEntryNotFoundException;
import dev.eugenlinde.activitytracker.steps.StepEntryRepository;
import dev.eugenlinde.activitytracker.steps.dto.StepsResponse;
import dev.eugenlinde.activitytracker.steps.model.StepEntry;
import dev.eugenlinde.activitytracker.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
@Transactional
public class StepsService {
    private static final int DEFAULT_RANGE_WEEKS = 8;

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

    public StepsResponse upsert(Long userId, LocalDate date, int steps) {
        entryDatePolicy.requireNotInFuture(date);
        StepEntry entry = stepEntryRepository
                .findByUserIdAndEntryDate(userId, date)
                .orElseGet(() -> newEntry(userId, date));
        entry.setSteps(steps);
        return StepsResponse.from(stepEntryRepository.saveAndFlush(entry));
    }

    public void delete(Long userId, LocalDate date) {
        StepEntry entry = stepEntryRepository
                .findByUserIdAndEntryDate(userId, date)
                .orElseThrow(StepEntryNotFoundException::new);
        stepEntryRepository.delete(entry);
    }

    private StepEntry newEntry(Long userId, LocalDate date) {
        StepEntry entry = new StepEntry();
        entry.setUser(userRepository.getReferenceById(userId));
        entry.setEntryDate(date);
        return entry;
    }
}
