package dev.eugenlinde.activitytracker.steps.dto;

import dev.eugenlinde.activitytracker.steps.model.StepEntry;

import java.time.LocalDate;

public record StepsResponse(LocalDate date, int steps) {
    public static StepsResponse from(StepEntry entry) {
        return new StepsResponse(entry.getEntryDate(), entry.getSteps());
    }
}
