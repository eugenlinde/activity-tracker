package dev.eugenlinde.activitytracker.share.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ShareRequest(
        @Email @NotNull @NotBlank String email
) {
}
