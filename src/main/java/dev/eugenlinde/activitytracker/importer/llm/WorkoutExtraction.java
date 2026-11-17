package dev.eugenlinde.activitytracker.importer.llm;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.util.List;

public record WorkoutExtraction(
        @NotNull @Valid @Size(max = 200) List<Draft> drafts
) {
    public record Draft(
            @NotBlank @Size(max = 100) String label,
            @Size(max = 255) String notes,
            @NotEmpty @Valid @Size(max = 12) List<DraftExercise> exercises
    ) {
    }

    public record DraftExercise(
            @NotBlank @Size(max = 200) String name,
            @Size(max = 50) String targetReps,
            @PositiveOrZero @Max(1000) Double weight,
            @Size(max = 50) String repsInReserve,
            @NotEmpty @Size(max = 12) List<@NotNull @PositiveOrZero @Max(1000) Integer> reps,
            @Size(max = 1000) String comment,
            @Size(max = 500) String videoUrl
    ) {
    }
}
