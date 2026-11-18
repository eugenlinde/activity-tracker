package dev.eugenlinde.activitytracker.importer.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;

public record WeightImport(
        @NotNull LocalDate date,
        @NotNull @DecimalMin("20.0") @DecimalMax("400.0") @Digits(integer = 3, fraction = 1) BigDecimal weightKg
) {
}
