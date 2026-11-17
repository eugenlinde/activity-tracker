package dev.eugenlinde.activitytracker.importer;

import dev.eugenlinde.activitytracker.activity.model.Exercise;
import dev.eugenlinde.activitytracker.activity.model.Session;
import dev.eugenlinde.activitytracker.activity.model.WorkoutSet;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ImportFingerprintTest {

    private static Session session(String name, String comment, String targetReps, WorkoutSet... sets) {
        return new Session("1", List.of(new Exercise(name, targetReps, null, comment, null, List.of(sets))));
    }

    @Test
    void of_isSixtyFourHexCharacters() {
        assertTrue(ImportFingerprint.of(session("Kükk", null, null, new WorkoutSet(100.0, 5))).matches("[0-9a-f]{64}"));
    }

    @Test
    void of_sameContent_sameFingerprint() {
        assertEquals(
                ImportFingerprint.of(session("Kükk", null, null, new WorkoutSet(100.0, 5), new WorkoutSet(100.0, 4))),
                ImportFingerprint.of(session("Kükk", null, null, new WorkoutSet(100.0, 5), new WorkoutSet(100.0, 4)))
        );
    }

    @Test
    void of_ignoresNameCaseSpacesCommentsAndTargets() {
        assertEquals(
                ImportFingerprint.of(session("Kükk", null, null, new WorkoutSet(100.0, 5))),
                ImportFingerprint.of(session("  kükk ", "raske", "5-8", new WorkoutSet(100.0, 5)))
        );
    }

    @Test
    void of_treatsWholeAndDecimalWeightsAlike() {
        assertEquals(
                ImportFingerprint.of(session("Kükk", null, null, new WorkoutSet(100.0, 5))),
                ImportFingerprint.of(session("Kükk", null, null, new WorkoutSet(100.00, 5)))
        );
    }

    @Test
    void of_changesWithWeightRepsSetCountNameOrOrder() {
        String base = ImportFingerprint.of(session("Kükk", null, null, new WorkoutSet(100.0, 5), new WorkoutSet(100.0, 4)));

        assertNotEquals(base, ImportFingerprint.of(session("Kükk", null, null, new WorkoutSet(102.5, 5), new WorkoutSet(100.0, 4))));
        assertNotEquals(base, ImportFingerprint.of(session("Kükk", null, null, new WorkoutSet(100.0, 6), new WorkoutSet(100.0, 4))));
        assertNotEquals(base, ImportFingerprint.of(session("Kükk", null, null, new WorkoutSet(100.0, 5))));
        assertNotEquals(base, ImportFingerprint.of(session("Jõutõmme", null, null, new WorkoutSet(100.0, 5), new WorkoutSet(100.0, 4))));
        assertNotEquals(base, ImportFingerprint.of(session("Kükk", null, null, new WorkoutSet(100.0, 4), new WorkoutSet(100.0, 5))));
    }

    @Test
    void of_missingWeightOrRepsIsNotTheSameAsZero() {
        assertNotEquals(
                ImportFingerprint.of(session("Lõuatõmbed", null, null, new WorkoutSet(0.0, 8))),
                ImportFingerprint.of(session("Lõuatõmbed", null, null, new WorkoutSet(null, 8)))
        );
    }

    @Test
    void of_twoExercisesAreNotTheSameAsOneWithJoinedSets() {
        Session two = new Session("1", List.of(
                new Exercise("Kükk", null, null, null, null, List.of(new WorkoutSet(100.0, 5))),
                new Exercise("Kükk", null, null, null, null, List.of(new WorkoutSet(100.0, 5)))
        ));

        assertNotEquals(
                ImportFingerprint.of(two),
                ImportFingerprint.of(session("Kükk", null, null, new WorkoutSet(100.0, 5), new WorkoutSet(100.0, 5)))
        );
    }
}
