package dev.eugenlinde.activitytracker.activity.model;

import jakarta.validation.constraints.PositiveOrZero;

public record WorkoutSet(
        @PositiveOrZero Double weight,
        @PositiveOrZero Integer reps
) {
}
