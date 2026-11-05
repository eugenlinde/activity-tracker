package dev.eugenlinde.activitytracker.activity.model;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

public record Session(
        @NotBlank String version,
        @Valid @Size(max = 12) List<Exercise> exerciseList
) {
}
