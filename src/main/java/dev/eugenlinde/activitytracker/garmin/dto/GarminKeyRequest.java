package dev.eugenlinde.activitytracker.garmin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record GarminKeyRequest(
        @NotBlank(message = "Key is required")
        @Size(max = 4096, message = "Key is too long")
        String apiKey
) {
    @Override
    public String toString() {
        return "GarminKeyRequest[apiKey=hidden]";
    }
}
