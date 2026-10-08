package dev.eugenlinde.activitytracker.activity.dto;

import dev.eugenlinde.activitytracker.activity.model.ActivityType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;

import java.time.Duration;
import java.time.Instant;

public record ActivityResponse(
        @NotNull Long id,
        @NotNull Long ownerId,
        @NotNull ActivityType activityType,
        @PastOrPresent @NotNull Instant startedAt,
        @NotNull Duration duration,
        String notes,
        @PastOrPresent @NotNull Instant createdAt,
        @PastOrPresent @NotNull Instant updatedAt
) {
}
