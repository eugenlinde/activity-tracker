package dev.eugenlinde.activitytracker.diet.dto;

import dev.eugenlinde.activitytracker.diet.model.DietEntry;
import dev.eugenlinde.activitytracker.diet.model.DietStatus;

import java.time.LocalDate;

public record DietResponse(LocalDate date, DietStatus status, String comment) {
    public static DietResponse from(DietEntry entry) {
        return new DietResponse(entry.getEntryDate(), entry.getStatus(), entry.getComment());
    }
}
