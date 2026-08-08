package dev.eugenlinde.activitytracker.authentication.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record SignupRequest(
        @Email @NotNull @NotBlank String email,
        @Size(min = 8) @NotNull String password,
        @Size(min = 2) @NotNull String firstName,
        @Size(min = 2) @NotNull String lastName
) {
}
