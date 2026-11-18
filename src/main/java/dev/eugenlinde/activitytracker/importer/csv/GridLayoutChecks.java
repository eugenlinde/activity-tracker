package dev.eugenlinde.activitytracker.importer.csv;

import dev.eugenlinde.activitytracker.importer.llm.GridLayout;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

public final class GridLayoutChecks {

    private static final int MAX_PROBLEMS = 10;
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    private GridLayoutChecks() {
    }

    public static List<String> problems(List<List<String>> rows, GridLayout layout) {
        List<String> problems = new ArrayList<>();
        if (layout.weeks().isEmpty() || layout.workouts().isEmpty()) {
            return problems;
        }
        columnsNamedOnce(layout, problems);
        weeksAlike(layout, problems);
        weekLabelsFromTheSheet(rows, layout, problems);
        workoutRows(layout, problems);
        workoutLabelsFromTheSheet(rows, layout, problems);
        noExerciseRowLeftOut(rows, layout, problems);
        return problems.size() > MAX_PROBLEMS ? List.copyOf(problems.subList(0, MAX_PROBLEMS)) : problems;
    }

    private static void columnsNamedOnce(GridLayout layout, List<String> problems) {
        Map<Integer, String> named = new HashMap<>();
        claim(named, problems, layout.nameColumn(), "nameColumn");
        claim(named, problems, layout.targetRepsColumn(), "targetRepsColumn");
        claim(named, problems, layout.videoColumn(), "videoColumn");
        for (int i = 0; i < layout.weeks().size(); i++) {
            GridLayout.Week week = layout.weeks().get(i);
            claim(named, problems, week.weightColumn(), "weeks[" + i + "].weightColumn");
            claim(named, problems, week.repsColumn(), "weeks[" + i + "].repsColumn");
            claim(named, problems, week.repsInReserveColumn(), "weeks[" + i + "].repsInReserveColumn");
            claim(named, problems, week.commentColumn(), "weeks[" + i + "].commentColumn");
        }
    }

    private static void claim(Map<Integer, String> named, List<String> problems, Integer column, String field) {
        if (column == null) {
            return;
        }
        String earlier = named.putIfAbsent(column, field);
        if (earlier != null) {
            problems.add(field + " and " + earlier + " both name column " + column + "; every column has one meaning");
        }
    }

    private static void weeksAlike(GridLayout layout, List<String> problems) {
        GridLayout.Week first = layout.weeks().getFirst();
        for (int i = 1; i < layout.weeks().size(); i++) {
            GridLayout.Week week = layout.weeks().get(i);
            boolean alike = Objects.equals(offset(first.weightColumn(), first), offset(week.weightColumn(), week))
                    && Objects.equals(offset(first.repsInReserveColumn(), first), offset(week.repsInReserveColumn(), week))
                    && Objects.equals(offset(first.commentColumn(), first), offset(week.commentColumn(), week));
            if (!alike) {
                problems.add("weeks[" + i + "]: its columns are not in the same order and spacing as in weeks[0]");
            }
            if (week.repsColumn() <= layout.weeks().get(i - 1).repsColumn()) {
                problems.add("weeks[" + i + "]: its repsColumn is not to the right of the one before; "
                        + "list the week blocks from left to right");
            }
        }
    }

    private static Integer offset(Integer column, GridLayout.Week week) {
        return column == null ? null : column - week.repsColumn();
    }

    private static void weekLabelsFromTheSheet(List<List<String>> rows, GridLayout layout, List<String> problems) {
        int firstWorkoutRow = layout.workouts().stream().mapToInt(GridLayout.Workout::firstRow).min().orElse(1);
        for (int i = 0; i < layout.weeks().size(); i++) {
            GridLayout.Week week = layout.weeks().get(i);
            int afterColumn = i == 0 ? -1 : layout.weeks().get(i - 1).repsColumn();
            if (!hasCell(rows, week.label(), 1, firstWorkoutRow - 1, afterColumn + 1, week.repsColumn())) {
                problems.add("weeks[" + i + "].label is not the text of a cell above the workouts and within "
                        + "that week block's columns");
            }
        }
    }

    private static void workoutRows(GridLayout layout, List<String> problems) {
        for (int i = 0; i < layout.workouts().size(); i++) {
            GridLayout.Workout workout = layout.workouts().get(i);
            if (workout.firstRow() > workout.lastRow()) {
                problems.add("workouts[" + i + "]: firstRow is after lastRow");
            }
            for (int j = 0; j < i; j++) {
                GridLayout.Workout earlier = layout.workouts().get(j);
                if (workout.firstRow() <= earlier.lastRow() && earlier.firstRow() <= workout.lastRow()) {
                    problems.add("workouts[" + i + "]: its rows overlap the rows of workouts[" + j + "]");
                }
            }
        }
    }

    private static void workoutLabelsFromTheSheet(List<List<String>> rows, GridLayout layout, List<String> problems) {
        for (int i = 0; i < layout.workouts().size(); i++) {
            GridLayout.Workout workout = layout.workouts().get(i);
            int previousLastRow = layout.workouts().stream()
                    .mapToInt(GridLayout.Workout::lastRow)
                    .filter(lastRow -> lastRow < workout.firstRow())
                    .max().orElse(0);
            if (!hasCell(rows, workout.label(), previousLastRow + 1, workout.firstRow(), 0, Integer.MAX_VALUE)) {
                problems.add("workouts[" + i + "].label is not the text of a cell between the workout before "
                        + "and this workout's first row");
            }
        }
    }

    private static void noExerciseRowLeftOut(List<List<String>> rows, GridLayout layout, List<String> problems) {
        for (int number = 1; number <= rows.size(); number++) {
            int rowNumber = number;
            boolean inAWorkout = layout.workouts().stream()
                    .anyMatch(workout -> workout.firstRow() <= rowNumber && rowNumber <= workout.lastRow());
            List<String> row = rows.get(number - 1);
            if (inAWorkout || GridReader.cell(row, layout.nameColumn()) == null) {
                continue;
            }
            boolean hasRepetitions = layout.weeks().stream()
                    .map(week -> GridReader.cell(row, week.repsColumn()))
                    .anyMatch(cell -> cell != null && GridReader.isRepetitions(cell));
            if (hasRepetitions) {
                problems.add("row " + number + " has an exercise name and repetitions but belongs to no workout");
            }
        }
    }

    private static boolean hasCell(
            List<List<String>> rows, String text, int firstRow, int lastRow, int firstColumn, int lastColumn
    ) {
        String wanted = normalise(text);
        for (int number = Math.max(firstRow, 1); number <= Math.min(lastRow, rows.size()); number++) {
            List<String> row = rows.get(number - 1);
            for (int column = Math.max(firstColumn, 0); column < row.size() && column <= lastColumn; column++) {
                if (normalise(row.get(column)).equals(wanted)) {
                    return true;
                }
            }
        }
        return false;
    }

    static String normalise(String text) {
        return WHITESPACE.matcher(text.replace(' ', ' ').replace(' ', ' ').strip()).replaceAll(" ");
    }
}
