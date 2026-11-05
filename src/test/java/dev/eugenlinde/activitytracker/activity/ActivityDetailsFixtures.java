package dev.eugenlinde.activitytracker.activity;

import dev.eugenlinde.activitytracker.activity.model.Exercise;
import dev.eugenlinde.activitytracker.activity.model.Session;
import dev.eugenlinde.activitytracker.activity.model.WorkoutSet;

import java.util.List;

public final class ActivityDetailsFixtures {

    private ActivityDetailsFixtures() {
    }

    public static Session session() {
        return new Session("1", List.of(
                exercise("Vertikaaltõmbed trenažööril pealthaare", List.of(
                        new WorkoutSet(31.25, 15),
                        new WorkoutSet(31.25, 14),
                        new WorkoutSet(31.25, null)
                )),
                exercise("Kerepainutused GHD pingil", List.of(
                        new WorkoutSet(0.0, 20)
                ))
        ));
    }

    public static Session sessionWithExercises(List<Exercise> exercises) {
        return new Session("1", exercises);
    }

    public static Exercise exercise(String name, List<WorkoutSet> sets) {
        return new Exercise(name, "10-15", "1-2", "filmi, KP", "https://youtu.be/OB9g-dq2yQM", sets);
    }
}
