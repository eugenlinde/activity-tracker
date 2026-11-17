package dev.eugenlinde.activitytracker.importer.dto;

import dev.eugenlinde.activitytracker.activity.model.Session;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public record WorkoutImport(
        @NotNull @PastOrPresent Instant startedAt,
        @NotNull @Valid Session details,
        @Size(max = 255) String notes
) {
}
