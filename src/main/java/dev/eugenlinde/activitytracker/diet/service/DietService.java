package dev.eugenlinde.activitytracker.diet.service;

import dev.eugenlinde.activitytracker.common.EntryDatePolicy;
import dev.eugenlinde.activitytracker.common.exception.DietEntryNotFoundException;
import dev.eugenlinde.activitytracker.common.exception.InvalidDateRangeException;
import dev.eugenlinde.activitytracker.diet.DietEntryRepository;
import dev.eugenlinde.activitytracker.diet.dto.DietResponse;
import dev.eugenlinde.activitytracker.diet.model.DietEntry;
import dev.eugenlinde.activitytracker.diet.model.DietStatus;
import dev.eugenlinde.activitytracker.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
@Transactional
public class DietService {
    private static final int MAX_RANGE_DAYS = 366;

    private final DietEntryRepository dietEntryRepository;
    private final UserRepository userRepository;
    private final EntryDatePolicy entryDatePolicy;

    public DietService(
            DietEntryRepository dietEntryRepository,
            UserRepository userRepository,
            EntryDatePolicy entryDatePolicy
    ) {
        this.dietEntryRepository = dietEntryRepository;
        this.userRepository = userRepository;
        this.entryDatePolicy = entryDatePolicy;
    }

    @Transactional(readOnly = true)
    public List<DietResponse> findAll(Long userId, LocalDate from, LocalDate to) {
        if (from.isAfter(to)) {
            throw new InvalidDateRangeException("'from' must not be after 'to'");
        }
        if (ChronoUnit.DAYS.between(from, to) + 1 > MAX_RANGE_DAYS) {
            throw new InvalidDateRangeException("A range covers at most 366 days");
        }
        return dietEntryRepository
                .findAllByUserIdAndEntryDateBetweenOrderByEntryDateAsc(userId, from, to)
                .stream()
                .map(DietResponse::from)
                .toList();
    }

    public DietResponse upsert(Long userId, LocalDate date, DietStatus status, String comment) {
        entryDatePolicy.requireNotInFuture(date);
        DietEntry entry = dietEntryRepository
                .findByUserIdAndEntryDate(userId, date)
                .orElseGet(() -> newEntry(userId, date));
        entry.setStatus(status);
        entry.setComment(normalize(comment));
        return DietResponse.from(dietEntryRepository.saveAndFlush(entry));
    }

    public void delete(Long userId, LocalDate date) {
        DietEntry entry = dietEntryRepository
                .findByUserIdAndEntryDate(userId, date)
                .orElseThrow(DietEntryNotFoundException::new);
        dietEntryRepository.delete(entry);
    }

    private String normalize(String comment) {
        if (comment == null) {
            return null;
        }
        String trimmed = comment.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private DietEntry newEntry(Long userId, LocalDate date) {
        DietEntry entry = new DietEntry();
        entry.setUser(userRepository.getReferenceById(userId));
        entry.setEntryDate(date);
        return entry;
    }
}
