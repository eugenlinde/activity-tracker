package dev.eugenlinde.activitytracker.importer.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record ImportConfirmRequest(
        @NotNull @Valid @Size(max = 200) List<WorkoutImport> workouts,
        @NotNull @Valid @Size(max = 5000) List<WeightImport> weights,
        @NotNull @Valid @Size(max = 5000) List<StepsImport> steps
) {
}
