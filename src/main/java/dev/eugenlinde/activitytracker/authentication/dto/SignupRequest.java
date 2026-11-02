package dev.eugenlinde.activitytracker.authentication.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record SignupRequest(
        @Email @NotNull @NotBlank @Size(max = 255) String email,
        @Size(min = 8, max = 72) @NotNull String password,
        @Size(min = 2, max = 255) @NotNull String firstName,
        @Size(min = 2, max = 255) @NotNull String lastName
) {
}
