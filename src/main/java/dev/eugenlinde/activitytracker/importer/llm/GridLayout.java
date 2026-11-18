package dev.eugenlinde.activitytracker.importer.llm;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.util.List;

public record GridLayout(
        @NotNull @PositiveOrZero Integer nameColumn,
        @PositiveOrZero Integer targetRepsColumn,
        @PositiveOrZero Integer videoColumn,
        @NotNull @Size(max = 60) List<@NotNull @Valid Week> weeks,
        @NotNull @Size(max = 30) List<@NotNull @Valid Workout> workouts
) {

    public record Week(
            @NotBlank @Size(max = 100) String label,
            @PositiveOrZero Integer weightColumn,
            @NotNull @PositiveOrZero Integer repsColumn,
            @PositiveOrZero Integer repsInReserveColumn,
            @PositiveOrZero Integer commentColumn
    ) {
    }

    public record Workout(
            @NotBlank @Size(max = 100) String label,
            @NotNull @Positive Integer firstRow,
            @NotNull @Positive Integer lastRow
    ) {
    }
}
