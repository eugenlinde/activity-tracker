package dev.eugenlinde.activitytracker.importer;

import dev.eugenlinde.activitytracker.activity.model.Exercise;
import dev.eugenlinde.activitytracker.activity.model.Session;
import dev.eugenlinde.activitytracker.activity.model.WorkoutSet;
import dev.eugenlinde.activitytracker.importer.llm.WorkoutExtraction;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WorkoutDraftMapperTest {

    @Test
    void toSession_expandsOneWeightAndARepsListIntoSets() {
        WorkoutExtraction.Draft draft = new WorkoutExtraction.Draft("Nädal 1 / Treening 1", "märkus", List.of(
                new WorkoutExtraction.DraftExercise("Kükk", "5-8", 100.0, "1-2", List.of(8, 7, 6), "raske", null),
                new WorkoutExtraction.DraftExercise("Lõuatõmbed", null, 0.0, null, List.of(10), null, "https://example.com/v")
        ));

        Session session = WorkoutDraftMapper.toSession(draft);

        assertEquals(new Session("1", List.of(
                new Exercise("Kükk", "5-8", "1-2", "raske", null, List.of(
                        new WorkoutSet(100.0, 8), new WorkoutSet(100.0, 7), new WorkoutSet(100.0, 6)
                )),
                new Exercise("Lõuatõmbed", null, null, null, "https://example.com/v", List.of(new WorkoutSet(0.0, 10)))
        )), session);
    }

    @Test
    void toSession_missingWeight_staysMissingOnEverySet() {
        WorkoutExtraction.Draft draft = new WorkoutExtraction.Draft("x", null, List.of(
                new WorkoutExtraction.DraftExercise("Plank", null, null, null, List.of(60, 45), null, null)
        ));

        assertEquals(
                List.of(new WorkoutSet(null, 60), new WorkoutSet(null, 45)),
                WorkoutDraftMapper.toSession(draft).exerciseList().getFirst().workoutSetList()
        );
    }
}
