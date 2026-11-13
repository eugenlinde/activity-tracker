package dev.eugenlinde.activitytracker.weight.service;

import dev.eugenlinde.activitytracker.common.exception.InvalidEntryDateException;
import dev.eugenlinde.activitytracker.common.exception.WeightEntryNotFoundException;
import dev.eugenlinde.activitytracker.user.UserRepository;
import dev.eugenlinde.activitytracker.weight.WeightEntryRepository;
import dev.eugenlinde.activitytracker.weight.dto.WeightResponse;
import dev.eugenlinde.activitytracker.weight.model.WeightEntry;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

@Service
@Transactional
public class WeightService {
    private static final int DEFAULT_RANGE_WEEKS = 12;

    private final WeightEntryRepository weightEntryRepository;
    private final UserRepository userRepository;
    private final Clock clock;

    public WeightService(
            WeightEntryRepository weightEntryRepository,
            UserRepository userRepository,
            Clock clock
    ) {
        this.weightEntryRepository = weightEntryRepository;
        this.userRepository = userRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<WeightResponse> findAll(Long userId, LocalDate from, LocalDate to) {
        LocalDate rangeEnd = to != null ? to : latestAllowedDate();
        LocalDate rangeStart = from != null ? from : LocalDate.now(clock).minusWeeks(DEFAULT_RANGE_WEEKS);
        return weightEntryRepository
                .findAllByUserIdAndEntryDateBetweenOrderByEntryDateAsc(userId, rangeStart, rangeEnd)
                .stream()
                .map(WeightResponse::from)
                .toList();
    }

    public WeightResponse upsert(Long userId, LocalDate date, BigDecimal weightKg) {
        if (date.isAfter(latestAllowedDate())) {
            throw new InvalidEntryDateException();
        }
        WeightEntry entry = weightEntryRepository
                .findByUserIdAndEntryDate(userId, date)
                .orElseGet(() -> newEntry(userId, date));
        entry.setWeightKg(weightKg);
        return WeightResponse.from(weightEntryRepository.saveAndFlush(entry));
    }

    public void delete(Long userId, LocalDate date) {
        WeightEntry entry = weightEntryRepository
                .findByUserIdAndEntryDate(userId, date)
                .orElseThrow(WeightEntryNotFoundException::new);
        weightEntryRepository.delete(entry);
    }

    private LocalDate latestAllowedDate() {
        return LocalDate.now(clock).plusDays(1);
    }

    private WeightEntry newEntry(Long userId, LocalDate date) {
        WeightEntry entry = new WeightEntry();
        entry.setUser(userRepository.getReferenceById(userId));
        entry.setEntryDate(date);
        return entry;
    }
}
