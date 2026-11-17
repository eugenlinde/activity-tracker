package dev.eugenlinde.activitytracker.importer.dto;

import java.time.LocalDate;

public record StepsPreview(LocalDate date, int steps, Integer existingSteps) {
}
