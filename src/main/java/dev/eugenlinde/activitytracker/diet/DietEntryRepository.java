package dev.eugenlinde.activitytracker.diet;

import dev.eugenlinde.activitytracker.diet.model.DietEntry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface DietEntryRepository extends JpaRepository<DietEntry, Long> {
    List<DietEntry> findAllByUserIdAndEntryDateBetweenOrderByEntryDateAsc(Long userId, LocalDate from, LocalDate to);

    Optional<DietEntry> findByUserIdAndEntryDate(Long userId, LocalDate entryDate);
}
