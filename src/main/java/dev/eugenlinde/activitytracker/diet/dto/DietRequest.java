package dev.eugenlinde.activitytracker.diet.dto;

import dev.eugenlinde.activitytracker.diet.model.DietStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record DietRequest(
        @NotNull
        DietStatus status,
        @Size(max = 500)
        String comment
) {
}
