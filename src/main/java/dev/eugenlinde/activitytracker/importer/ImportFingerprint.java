package dev.eugenlinde.activitytracker.importer;

import dev.eugenlinde.activitytracker.activity.model.Exercise;
import dev.eugenlinde.activitytracker.activity.model.Session;
import dev.eugenlinde.activitytracker.activity.model.WorkoutSet;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;

public final class ImportFingerprint {

    private ImportFingerprint() {
    }

    public static String of(Session session) {
        StringBuilder canonical = new StringBuilder();
        List<Exercise> exercises = session.exerciseList() == null ? List.of() : session.exerciseList();
        for (Exercise exercise : exercises) {
            canonical.append(exercise.name().strip().toLowerCase(Locale.ROOT)).append('\u001f');
            List<WorkoutSet> sets = exercise.workoutSetList() == null ? List.of() : exercise.workoutSetList();
            for (WorkoutSet set : sets) {
                canonical.append(weight(set.weight())).append('x').append(set.reps() == null ? "-" : set.reps()).append(';');
            }
            canonical.append('\u001e');
        }
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(canonical.toString().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String weight(Double weight) {
        if (weight == null) {
            return "-";
        }
        return BigDecimal.valueOf(weight).stripTrailingZeros().toPlainString();
    }
}
