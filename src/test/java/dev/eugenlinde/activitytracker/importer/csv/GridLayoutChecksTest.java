package dev.eugenlinde.activitytracker.importer.csv;

import dev.eugenlinde.activitytracker.importer.llm.GridLayout;
import dev.eugenlinde.activitytracker.importer.llm.GridLayout.Week;
import dev.eugenlinde.activitytracker.importer.llm.GridLayout.Workout;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GridLayoutChecksTest {

    private static final List<List<String>> ROWS = CsvText.parse("""
            KAVA,,,,,,,,,,,
            ,,,Nädal 1,,,,,Nädal  2,,,
            ,Treening 1,,Raskus,Varus,Kordused,Kommentaar,,Raskus,Varus,Kordused,Kommentaar
            5-8,Kükk,,100,3,"8, 7",,,"102,5",1-2,"8, 7, 6",RP
            8-12,Surumine,,60,3,"10, 9",,,60,1-2,"10, 9",KP
            ,,,,,,,,,,,
            ,Treening 2,,,,,,,,,,
            10-15,Lõuatõmbed,,0,3,"12, 12",,,,,,
            ,,,KKK,,,,,,,,
            """);

    private static final Week WEEK_1 = new Week("Nädal 1", 3, 5, 4, 6);
    private static final Week WEEK_2 = new Week("Nädal 2", 8, 10, 9, 11);
    private static final Workout WORKOUT_1 = new Workout("Treening 1", 4, 5);
    private static final Workout WORKOUT_2 = new Workout("Treening 2", 8, 8);

    private static GridLayout layout(List<Week> weeks, List<Workout> workouts) {
        return new GridLayout(1, 0, 2, weeks, workouts);
    }

    private static List<String> problems(GridLayout layout) {
        return GridLayoutChecks.problems(ROWS, layout);
    }

    private static void assertOneProblemAbout(GridLayout layout, String... fragments) {
        List<String> problems = problems(layout);
        assertFalse(problems.isEmpty(), "expected a problem");
        for (String fragment : fragments) {
            assertTrue(problems.stream().anyMatch(problem -> problem.contains(fragment)), fragment + " not in " + problems);
        }
    }

    @Test
    void problems_layoutThatMatchesTheSheet_hasNone() {
        assertEquals(List.of(), problems(layout(List.of(WEEK_1, WEEK_2), List.of(WORKOUT_1, WORKOUT_2))));
    }

    @Test
    void problems_sheetWithoutThisLayout_hasNone() {
        assertEquals(List.of(), problems(layout(List.of(), List.of())));
    }

    @Test
    void problems_repetitionsInTheNameColumn() {
        assertOneProblemAbout(layout(List.of(new Week("Nädal 1", 3, 1, 4, 6)), List.of(WORKOUT_1, WORKOUT_2)),
                "nameColumn", "weeks[0].repsColumn");
    }

    @Test
    void problems_twoWeeksReadingTheSameWeightColumn() {
        assertOneProblemAbout(layout(List.of(WEEK_1, new Week("Nädal 2", 3, 10, 9, 11)), List.of(WORKOUT_1, WORKOUT_2)),
                "weeks[0].weightColumn", "weeks[1].weightColumn");
    }

    @Test
    void problems_weightAndRepetitionsInTheSameColumn() {
        assertOneProblemAbout(layout(List.of(new Week("Nädal 1", 5, 5, 4, 6)), List.of(WORKOUT_1, WORKOUT_2)),
                "weeks[0].weightColumn", "weeks[0].repsColumn");
    }

    @Test
    void problems_oneColumnOfAWeekSlipped() {
        assertOneProblemAbout(layout(List.of(WEEK_1, new Week("Nädal 2", 8, 10, 7, 11)), List.of(WORKOUT_1, WORKOUT_2)),
                "weeks[1]", "same order");
    }

    @Test
    void problems_weekBlockWithAColumnTheFirstOneLacks() {
        assertOneProblemAbout(layout(List.of(WEEK_1, new Week("Nädal 2", 8, 10, 9, null)), List.of(WORKOUT_1, WORKOUT_2)),
                "weeks[1]", "same order");
    }

    @Test
    void problems_weeksNotFromLeftToRight() {
        assertOneProblemAbout(layout(List.of(WEEK_2, WEEK_1), List.of(WORKOUT_1, WORKOUT_2)),
                "weeks[1]", "left to right");
    }

    @Test
    void problems_weekLabelsSwapped() {
        assertOneProblemAbout(layout(
                        List.of(new Week("Nädal 2", 3, 5, 4, 6), new Week("Nädal 1", 8, 10, 9, 11)),
                        List.of(WORKOUT_1, WORKOUT_2)),
                "weeks[0].label", "weeks[1].label");
    }

    @Test
    void problems_weekLabelThatIsNotInTheSheet() {
        assertOneProblemAbout(layout(List.of(WEEK_1, new Week("Week 2", 8, 10, 9, 11)), List.of(WORKOUT_1, WORKOUT_2)),
                "weeks[1].label");
    }

    @Test
    void problems_weekLabelFoundOnlyBelowTheFirstWorkout_doesNotCount() {
        assertOneProblemAbout(layout(List.of(new Week("Kükk", 3, 5, 4, 6)), List.of(WORKOUT_1, WORKOUT_2)),
                "weeks[0].label");
    }

    @Test
    void problems_workoutLabelOfAnotherWorkout() {
        assertOneProblemAbout(layout(List.of(WEEK_1, WEEK_2), List.of(WORKOUT_1, new Workout("Treening 1", 8, 8))),
                "workouts[1].label");
    }

    @Test
    void problems_workoutLabelThatIsNotInTheSheet() {
        assertOneProblemAbout(layout(List.of(WEEK_1, WEEK_2), List.of(new Workout("Workout A", 4, 5), WORKOUT_2)),
                "workouts[0].label");
    }

    @Test
    void problems_workoutRowsBackwards() {
        assertOneProblemAbout(layout(List.of(WEEK_1, WEEK_2), List.of(new Workout("Treening 1", 5, 4), WORKOUT_2)),
                "workouts[0]", "firstRow is after lastRow");
    }

    @Test
    void problems_workoutsSharingRows() {
        assertOneProblemAbout(layout(List.of(WEEK_1, WEEK_2), List.of(WORKOUT_1, new Workout("Treening 2", 5, 8))),
                "workouts[1]", "overlap");
    }

    @Test
    void problems_exerciseRowLeftOutsideEveryWorkout() {
        assertOneProblemAbout(layout(List.of(WEEK_1, WEEK_2), List.of(new Workout("Treening 1", 4, 4), WORKOUT_2)),
                "row 5", "no workout");
    }

    @Test
    void problems_neverQuoteTheSheet() {
        List<GridLayout> wrong = List.of(
                layout(List.of(new Week("Nädal 2", 3, 5, 4, 6), new Week("Nädal 1", 8, 10, 7, null)),
                        List.of(new Workout("Treening 2", 4, 4), new Workout("Salajane", 5, 8))),
                layout(List.of(WEEK_2, WEEK_1), List.of(WORKOUT_2, WORKOUT_1))
        );

        for (GridLayout layout : wrong) {
            for (String problem : problems(layout)) {
                for (String text : List.of("Nädal", "Treening", "Kükk", "Surumine", "Salajane", "KAVA")) {
                    assertFalse(problem.contains(text), problem);
                }
            }
        }
    }

    @Test
    void problems_longListIsCutShort() {
        StringBuilder sheet = new StringBuilder(",Treening 1,N1\n");
        for (int i = 0; i < 40; i++) {
            sheet.append("x,Harjutus ").append(i).append(",8\n");
        }

        List<String> problems = GridLayoutChecks.problems(CsvText.parse(sheet.toString()),
                new GridLayout(1, null, null, List.of(new Week("N1", null, 2, null, null)),
                        List.of(new Workout("Treening 1", 2, 2))));

        assertTrue(problems.size() <= 10, "problems: " + problems.size());
    }
}
