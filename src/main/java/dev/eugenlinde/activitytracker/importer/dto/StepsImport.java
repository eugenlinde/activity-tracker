package dev.eugenlinde.activitytracker.importer.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record StepsImport(
        @NotNull LocalDate date,
        @NotNull @Min(0) @Max(100000) Integer steps
) {
}
