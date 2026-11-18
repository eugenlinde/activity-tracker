package dev.eugenlinde.activitytracker.importer;

import dev.eugenlinde.activitytracker.activity.model.Exercise;
import dev.eugenlinde.activitytracker.activity.model.Session;
import dev.eugenlinde.activitytracker.activity.model.WorkoutSet;

public final class WorkoutDraftMapper {

    private static final String SESSION_VERSION = "1";

    private WorkoutDraftMapper() {
    }

    public static Session toSession(WorkoutDraft draft) {
        return new Session(SESSION_VERSION, draft.exercises().stream().map(WorkoutDraftMapper::toExercise).toList());
    }

    private static Exercise toExercise(WorkoutDraft.DraftExercise exercise) {
        return new Exercise(
                exercise.name(),
                exercise.targetReps(),
                exercise.repsInReserve(),
                exercise.comment(),
                exercise.videoUrl(),
                exercise.reps().stream().map(reps -> new WorkoutSet(exercise.weight(), reps)).toList()
        );
    }
}
