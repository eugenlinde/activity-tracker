package dev.eugenlinde.activitytracker.weight;

import dev.eugenlinde.activitytracker.weight.model.WeightEntry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface WeightEntryRepository extends JpaRepository<WeightEntry, Long> {
    List<WeightEntry> findAllByUserIdAndEntryDateBetweenOrderByEntryDateAsc(Long userId, LocalDate from, LocalDate to);

    Optional<WeightEntry> findByUserIdAndEntryDate(Long userId, LocalDate entryDate);
}
