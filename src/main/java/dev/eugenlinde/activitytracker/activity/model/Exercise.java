package dev.eugenlinde.activitytracker.activity.model;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

public record Exercise(
        @NotBlank String name,
        String targetReps,
        String repsInReserve,
        @Min(1) @Max(12) Integer targetSets,
        String comment,
        String videoUrl,
        @Valid @Size(max = 12) List<WorkoutSet> workoutSetList

) {
}
