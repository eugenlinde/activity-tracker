package dev.eugenlinde.activitytracker.importer;

import java.util.List;

public record WorkoutDraft(String label, List<DraftExercise> exercises) {

    public record DraftExercise(
            String name,
            String targetReps,
            Double weight,
            String repsInReserve,
            List<Integer> reps,
            String comment,
            String videoUrl
    ) {
    }
}
