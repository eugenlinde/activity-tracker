package dev.eugenlinde.activitytracker.steps.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record StepsGoalRequest(
        @NotNull
        @DecimalMin("1")
        @DecimalMax("100000")
        @Digits(integer = 6, fraction = 0)
        BigDecimal goal
) {
}
