package dev.eugenlinde.activitytracker.importer.csv;

import dev.eugenlinde.activitytracker.importer.WorkoutDraft;
import dev.eugenlinde.activitytracker.importer.WorkoutDraft.DraftExercise;
import dev.eugenlinde.activitytracker.importer.llm.GridLayout;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class GridReader {

    private static final int MAX_SETS = 12;
    private static final int MAX_CELL_LENGTH = 60;
    private static final int MAX_REPS = 1000;
    private static final double MAX_WEIGHT = 1000;
    private static final Pattern REPS = Pattern.compile("\\d{1,4}(?:\\s*[,;/]\\s*\\d{1,4}|\\s+\\d{1,4})*");
    private static final Pattern NUMBER = Pattern.compile("\\d+");
    private static final Pattern WEIGHT = Pattern.compile("(\\d{1,4}(?:[.,]\\d{1,3})?)\\s*(?:kg)?", Pattern.CASE_INSENSITIVE);

    private GridReader() {
    }

    public static GridRows read(List<List<String>> rows, GridLayout layout) {
        List<WorkoutDraft> drafts = new ArrayList<>();
        List<SkippedEntry> skipped = new ArrayList<>();
        for (GridLayout.Week week : layout.weeks()) {
            for (GridLayout.Workout workout : layout.workouts()) {
                String label = week.label().strip() + " / " + workout.label().strip();
                List<DraftExercise> exercises = new ArrayList<>();
                int last = Math.min(workout.lastRow(), rows.size());
                for (int number = workout.firstRow(); number <= last; number++) {
                    List<String> row = rows.get(number - 1);
                    String name = cell(row, layout.nameColumn());
                    String repsCell = cell(row, week.repsColumn());
                    if (repsCell == null) {
                        continue;
                    }
                    if (name == null) {
                        skipped.add(new SkippedEntry(number, label, null));
                        continue;
                    }
                    Optional<List<Integer>> reps = reps(repsCell);
                    String weightCell = cell(row, week.weightColumn());
                    Optional<Double> weight = weightCell == null ? Optional.empty() : weight(weightCell);
                    if (reps.isEmpty() || (weightCell != null && weight.isEmpty())) {
                        skipped.add(new SkippedEntry(number, label, name));
                        continue;
                    }
                    exercises.add(new DraftExercise(
                            name,
                            cell(row, layout.targetRepsColumn()),
                            weight.orElse(null),
                            cell(row, week.repsInReserveColumn()),
                            reps.get(),
                            cell(row, week.commentColumn()),
                            webAddress(cell(row, layout.videoColumn()))
                    ));
                }
                if (!exercises.isEmpty()) {
                    drafts.add(new WorkoutDraft(label, exercises));
                }
            }
        }
        return new GridRows(drafts, skipped);
    }

    static String cell(List<String> row, Integer column) {
        if (column == null || column < 0 || column >= row.size()) {
            return null;
        }
        String value = row.get(column).replace('\u00a0', ' ').replace('\u202f', ' ').strip();
        return value.isEmpty() ? null : value;
    }

    static boolean isRepetitions(String cell) {
        return reps(cell).isPresent();
    }

    private static Optional<List<Integer>> reps(String cell) {
        if (cell.length() > MAX_CELL_LENGTH || !REPS.matcher(cell).matches()) {
            return Optional.empty();
        }
        List<Integer> reps = new ArrayList<>();
        Matcher numbers = NUMBER.matcher(cell);
        while (numbers.find()) {
            int value = Integer.parseInt(numbers.group());
            if (value > MAX_REPS) {
                return Optional.empty();
            }
            reps.add(value);
        }
        return reps.size() > MAX_SETS ? Optional.empty() : Optional.of(reps);
    }

    private static Optional<Double> weight(String cell) {
        Matcher matcher = WEIGHT.matcher(cell);
        if (cell.length() > MAX_CELL_LENGTH || !matcher.matches()) {
            return Optional.empty();
        }
        double value = Double.parseDouble(matcher.group(1).replace(',', '.'));
        return value > MAX_WEIGHT ? Optional.empty() : Optional.of(value);
    }

    private static String webAddress(String cell) {
        if (cell == null) {
            return null;
        }
        String lower = cell.toLowerCase(Locale.ROOT);
        return lower.startsWith("http://") || lower.startsWith("https://") ? cell : null;
    }
}
