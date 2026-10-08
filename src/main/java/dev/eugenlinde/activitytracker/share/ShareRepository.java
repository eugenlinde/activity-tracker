package dev.eugenlinde.activitytracker.share;

import dev.eugenlinde.activitytracker.share.model.Share;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ShareRepository extends JpaRepository<Share, Long> {
    @EntityGraph(attributePaths = "viewer")
    List<Share> findAllByOwnerId(Long ownerId);

    @EntityGraph(attributePaths = "owner")
    List<Share> findAllByViewerId(Long viewerId);

    Optional<Share> findByIdAndOwnerId(Long id, Long ownerId);

    boolean existsByOwnerIdAndViewerId(Long ownerId, Long viewerId);
}
