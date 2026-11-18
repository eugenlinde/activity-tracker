package dev.eugenlinde.activitytracker.importer.dto;

import java.util.List;

public record ImportPreviewResponse(
        String kind,
        List<WorkoutPreview> workouts,
        List<WeightPreview> weights,
        List<StepsPreview> steps,
        int unchangedWeights,
        int unchangedSteps,
        int unreadableRows,
        List<SkippedEntryPreview> skippedEntries
) {
}
