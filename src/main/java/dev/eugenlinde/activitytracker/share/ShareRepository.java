package dev.eugenlinde.activitytracker.share;

import dev.eugenlinde.activitytracker.share.model.Share;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ShareRepository extends JpaRepository<Share, Long> {
    List<Share> findAllByOwnerId(Long ownerId);

    Optional<Share> findByIdAndOwnerId(Long id, Long ownerId);

    boolean existsByOwnerIdAndViewerId(Long ownerId, Long viewerId);
}
