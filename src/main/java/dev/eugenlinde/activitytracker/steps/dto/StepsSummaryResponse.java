package dev.eugenlinde.activitytracker.steps.dto;

import java.time.LocalDate;

public record StepsSummaryResponse(
        LocalDate from,
        LocalDate to,
        int daysLogged,
        long totalSteps,
        long averagePerLoggedDay,
        StepsResponse bestDay
) {}
