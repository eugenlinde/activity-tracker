package dev.eugenlinde.activitytracker.activity;

import dev.eugenlinde.activitytracker.activity.model.Activity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ActivityRepository extends JpaRepository<Activity, Long> {
    List<Activity> findAllByUserId(Long userId);

    Optional<Activity> findByIdAndUserId(Long id, Long userId);
}
