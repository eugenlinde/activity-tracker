package dev.eugenlinde.activitytracker.media;

import dev.eugenlinde.activitytracker.media.model.Media;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MediaRepository extends JpaRepository<Media, Long> {
    @EntityGraph(attributePaths = "activity.user")
    Optional<Media> findById(Long id);

    List<Media> findAllByActivityId(Long activityId);

}
