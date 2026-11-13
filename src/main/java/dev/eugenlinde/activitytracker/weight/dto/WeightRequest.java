package dev.eugenlinde.activitytracker.weight.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record WeightRequest(
        @NotNull
        @DecimalMin("20.0")
        @DecimalMax("400.0")
        @Digits(integer = 3, fraction = 1)
        BigDecimal weightKg
) {
}
