package dev.eugenlinde.activitytracker.garmin;

import dev.eugenlinde.activitytracker.garmin.model.GarminConnection;
import org.springframework.data.jpa.repository.JpaRepository;

import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface GarminConnectionRepository extends JpaRepository<GarminConnection, Long> {
    @Query("select c.user.id from GarminConnection c order by c.id")
    List<Long> findAllUserIds();

    Optional<GarminConnection> findByUserId(Long userId);

    boolean existsByUserId(Long userId);
}
