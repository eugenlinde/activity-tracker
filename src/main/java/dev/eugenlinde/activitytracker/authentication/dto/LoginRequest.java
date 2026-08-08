package dev.eugenlinde.activitytracker.authentication.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record LoginRequest(
        @Email @NotNull @NotBlank String email,
        @Size(min = 8) @NotNull String password
) {
}
