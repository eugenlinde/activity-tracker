package dev.eugenlinde.activitytracker.importer.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record WeightPreview(LocalDate date, BigDecimal weightKg, BigDecimal existingWeightKg) {
}
