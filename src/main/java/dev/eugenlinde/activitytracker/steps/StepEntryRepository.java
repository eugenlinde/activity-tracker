package dev.eugenlinde.activitytracker.steps;

import dev.eugenlinde.activitytracker.steps.model.StepEntry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface StepEntryRepository extends JpaRepository<StepEntry, Long> {
    List<StepEntry> findAllByUserIdAndEntryDateBetweenOrderByEntryDateAsc(Long userId, LocalDate from, LocalDate to);

    Optional<StepEntry> findByUserIdAndEntryDate(Long userId, LocalDate entryDate);
}
