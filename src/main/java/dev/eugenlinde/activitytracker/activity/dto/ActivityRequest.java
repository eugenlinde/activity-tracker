package dev.eugenlinde.activitytracker.activity.dto;

import dev.eugenlinde.activitytracker.activity.model.ActivityType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;

import java.time.Duration;
import java.time.Instant;

public record ActivityRequest(
        @NotNull ActivityType activityType,
        @NotNull @PastOrPresent Instant startedAt,
        @NotNull Duration duration,
        @Size(max = 255) String notes
) {
}
