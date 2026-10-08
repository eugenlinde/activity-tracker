package dev.eugenlinde.activitytracker.activity.mapper;

import dev.eugenlinde.activitytracker.activity.dto.ActivityResponse;
import dev.eugenlinde.activitytracker.activity.model.Activity;
import org.springframework.stereotype.Component;

@Component
public class ActivityMapper {
    public ActivityResponse mapToActivityResponse(Activity activity) {
        return new ActivityResponse(
                activity.getId(),
                activity.getUser().getId(),
                activity.getActivityType(),
                activity.getStartedAt(),
                activity.getDuration(),
                activity.getNotes(),
                activity.getCreatedAt(),
                activity.getUpdatedAt()
        );
    }
}
