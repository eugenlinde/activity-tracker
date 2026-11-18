package dev.eugenlinde.activitytracker.importer.csv;

import java.math.BigDecimal;
import java.time.LocalDate;

public record WeightRow(LocalDate date, BigDecimal weightKg) {
}
