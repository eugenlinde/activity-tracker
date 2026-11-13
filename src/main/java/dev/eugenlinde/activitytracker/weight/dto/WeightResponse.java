package dev.eugenlinde.activitytracker.weight.dto;

import dev.eugenlinde.activitytracker.weight.model.WeightEntry;

import java.math.BigDecimal;
import java.time.LocalDate;

public record WeightResponse(LocalDate date, BigDecimal weightKg) {
    public static WeightResponse from(WeightEntry entry) {
        return new WeightResponse(entry.getEntryDate(), entry.getWeightKg());
    }
}
