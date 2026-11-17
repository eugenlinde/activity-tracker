package dev.eugenlinde.activitytracker.activity;

import dev.eugenlinde.activitytracker.activity.model.Activity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ActivityRepository extends JpaRepository<Activity, Long> {
    List<Activity> findAllByUserId(Long userId);

    Optional<Activity> findByIdAndUserId(Long id, Long userId);

    @Query("""
            select a.importFingerprint as fingerprint, count(a) as total
            from Activity a
            where a.user.id = :userId and a.importFingerprint is not null
            group by a.importFingerprint
            """)
    List<FingerprintCount> countImportFingerprints(@Param("userId") Long userId);

    interface FingerprintCount {
        String getFingerprint();

        long getTotal();
    }
}
