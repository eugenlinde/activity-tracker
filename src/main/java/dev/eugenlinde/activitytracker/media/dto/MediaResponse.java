package dev.eugenlinde.activitytracker.media.dto;

import jakarta.validation.constraints.NotNull;

public record MediaResponse(
        @NotNull Long id
) {
}
