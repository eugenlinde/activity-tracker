package dev.eugenlinde.activitytracker.importer.csv;

import dev.eugenlinde.activitytracker.importer.WorkoutDraft;
import dev.eugenlinde.activitytracker.importer.WorkoutDraft.DraftExercise;
import dev.eugenlinde.activitytracker.importer.llm.GridLayout;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GridReaderTest {

    private static final String SHEET = """
            TREENINGKAVA,,,,,,,,,,,,,
            ,,,Nädal 1,,,,,,Nädal 2,,,,
            ,Treening 1,,Seeriad,Raskus,Varus,Kordused,Kommentaar,,Seeriad,Raskus,Varus,Kordused,Kommentaar
            5-20,Kükk,https://example.test/kukk,2,100,3,"8, 7",filmi,,3,"102,5",1-2,"8,7,6","filmi, RP"
            10-15,Lõuatõmbed,,2,0,3,"12,12",,,3,0,1-2,,KP
            ,,,,,,,,,,,,,
            ,Treening 2,,,,,,,,,,,,
            8-12,Rinnalt surumine,https://example.test/surumine,2,60 kg,3,10,,,,,,,
            ,,,KKK,,,,,,,,,,
            ,,,"Mida tähendab ""varus""? Pikk jutt, 12, 11",,,,,,,,,,
            """;

    private static final GridLayout LAYOUT = new GridLayout(
            1, 0, 2,
            List.of(new GridLayout.Week("Nädal 1", 4, 6, 5, 7), new GridLayout.Week("Nädal 2", 10, 12, 11, 13)),
            List.of(new GridLayout.Workout("Treening 1", 4, 5), new GridLayout.Workout("Treening 2", 8, 8))
    );

    private static GridRows read(String sheet, GridLayout layout) {
        return GridReader.read(CsvText.parse(sheet), layout);
    }

    @Test
    void read_oneDraftForEachWeekAndWorkoutThatHasRepetitions_weekByWeek() {
        GridRows grid = read(SHEET, LAYOUT);

        assertEquals(
                List.of("Nädal 1 / Treening 1", "Nädal 1 / Treening 2", "Nädal 2 / Treening 1"),
                grid.drafts().stream().map(WorkoutDraft::label).toList()
        );
        assertEquals(List.of(), grid.skipped());
    }

    @Test
    void read_takesEveryValueFromItsOwnCell() {
        WorkoutDraft first = read(SHEET, LAYOUT).drafts().getFirst();

        assertEquals(List.of(
                new DraftExercise("Kükk", "5-20", 100.0, "3", List.of(8, 7), "filmi", "https://example.test/kukk"),
                new DraftExercise("Lõuatõmbed", "10-15", 0.0, "3", List.of(12, 12), null, null)
        ), first.exercises());
    }

    @Test
    void read_weekTwoComesFromWeekTwoColumns_andLeavesOutExercisesWithoutRepetitions() {
        WorkoutDraft weekTwo = read(SHEET, LAYOUT).drafts().get(2);

        assertEquals(List.of(
                new DraftExercise("Kükk", "5-20", 102.5, "1-2", List.of(8, 7, 6), "filmi, RP", "https://example.test/kukk")
        ), weekTwo.exercises());
    }

    @Test
    void read_weightWithUnit_isRead() {
        WorkoutDraft second = read(SHEET, LAYOUT).drafts().get(1);

        assertEquals(60.0, second.exercises().getFirst().weight());
        assertEquals(List.of(10), second.exercises().getFirst().reps());
    }

    @Test
    void read_rowsOutsideTheWorkouts_areNeverRead() {
        List<List<String>> rows = List.of(
                List.of("x", "Kükk", "", "8", "50"),
                List.of("x", "Näidis väljaspool", "", "9", "60"),
                List.of("x", "Surumine", "", "7", "40")
        );
        GridLayout layout = new GridLayout(1, null, null,
                List.of(new GridLayout.Week("N1", 4, 3, null, null)),
                List.of(new GridLayout.Workout("T1", 1, 1), new GridLayout.Workout("T2", 3, 3)));

        GridRows grid = GridReader.read(rows, layout);

        assertEquals(List.of("Kükk", "Surumine"), grid.drafts().stream()
                .flatMap(draft -> draft.exercises().stream()).map(DraftExercise::name).toList());
        assertEquals(List.of(), grid.skipped());
    }

    @Test
    void read_skippedEntry_saysWhichRowWorkoutAndExercise() {
        GridRows grid = read(SHEET.replace("\"8,7,6\"", "3x8"), LAYOUT);

        assertEquals(List.of(new SkippedEntry(4, "Nädal 2 / Treening 1", "Kükk")), grid.skipped());
    }

    @Test
    void read_repetitionsWithoutAnExerciseName_areSkippedAndReported() {
        GridRows grid = GridReader.read(List.of(List.of("x", " ", "", "8, 8", "50")), oneCell());

        assertTrue(grid.drafts().isEmpty());
        assertEquals(List.of(new SkippedEntry(1, "N1 / T1", null)), grid.skipped());
    }

    @Test
    void read_veryLongCell_isSkippedWithoutBeingMatched() {
        String long_ = "1" + ",1".repeat(5000);

        GridRows repsTooLong = GridReader.read(List.of(List.of("x", "Kükk", "", long_, "50")), oneCell());
        GridRows weightTooLong = GridReader.read(List.of(List.of("x", "Kükk", "", "8", "1".repeat(5000))), oneCell());

        assertEquals(1, repsTooLong.skipped().size());
        assertEquals(1, weightTooLong.skipped().size());
    }

    @Test
    void read_noBreakSpaces_areTreatedAsSpaces() {
        GridRows grid = GridReader.read(
                List.of(List.of("x", "\u00a0Kükk\u00a0", "", "8,\u00a07", "62,5\u202fkg")), oneCell());

        assertEquals(new DraftExercise("Kükk", null, 62.5, null, List.of(8, 7), null, null),
                grid.drafts().getFirst().exercises().getFirst());
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "12, 11, 10 | 12,11,10",
            "12,11,10 | 12,11,10",
            "12 11 10 | 12,11,10",
            "12; 11 | 12,11",
            "12/11/10 | 12,11,10",
            "8 | 8",
            "0 | 0",
    })
    void read_repetitionCell_acceptedForms(String cell, String expected) {
        GridRows grid = GridReader.read(List.of(List.of("x", "Kükk", "", cell, "50")), oneCell());

        List<Integer> want = List.of(expected.split(",")).stream().map(Integer::valueOf).toList();
        assertEquals(want, grid.drafts().getFirst().exercises().getFirst().reps());
        assertEquals(List.of(), grid.skipped());
    }

    @ParameterizedTest
    @ValueSource(strings = {"3x10", "10+2", "kümme", "12, 11 (valu)", "-5", "12,,11", "1-2", "10.5", "12345", "1001",
            "1,2,3,4,5,6,7,8,9,10,11,12,13"})
    void read_repetitionCellThatIsNotAListOfWholeNumbers_isCountedAndSkipped(String cell) {
        GridRows grid = GridReader.read(List.of(List.of("x", "Kükk", "", cell, "50")), oneCell());

        assertTrue(grid.drafts().isEmpty());
        assertEquals(1, grid.skipped().size());
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "100 | 100.0",
            "102,5 | 102.5",
            "102.5 | 102.5",
            "31.25 | 31.25",
            "60 kg | 60.0",
            "60KG | 60.0",
            "0 | 0.0",
    })
    void read_weightCell_acceptedForms(String cell, double expected) {
        GridRows grid = GridReader.read(List.of(List.of("x", "Kükk", "", "8", cell)), oneCell());

        assertEquals(expected, grid.drafts().getFirst().exercises().getFirst().weight());
    }

    @Test
    void read_emptyWeight_isNoWeight() {
        GridRows grid = read("x,Kükk,,8,\n", oneCell());

        assertNull(grid.drafts().getFirst().exercises().getFirst().weight());
        assertEquals(List.of(), grid.skipped());
    }

    @ParameterizedTest
    @ValueSource(strings = {"raske", "20-25", "-5", "1001", "2x20", "20 lbs"})
    void read_weightCellThatIsNotANumber_isCountedAndTheExerciseSkipped(String cell) {
        GridRows grid = GridReader.read(List.of(List.of("x", "Kükk", "", "8", cell)), oneCell());

        assertTrue(grid.drafts().isEmpty());
        assertEquals(1, grid.skipped().size());
    }

    @Test
    void read_unreadableExercise_doesNotHideTheOthersInTheSameWorkout() {
        GridLayout layout = new GridLayout(1, null, null,
                List.of(new GridLayout.Week("N1", 4, 3, null, null)),
                List.of(new GridLayout.Workout("T1", 1, 2)));

        GridRows grid = read("x,Kükk,,3x10,50\nx,Surumine,,\"10, 9\",40\n", layout);

        assertEquals(List.of("Surumine"), grid.drafts().getFirst().exercises().stream().map(DraftExercise::name).toList());
        assertEquals(List.of(new SkippedEntry(1, "N1 / T1", "Kükk")), grid.skipped());
    }

    @Test
    void read_linkThatIsNotAWebAddress_isLeftOut() {
        GridLayout layout = new GridLayout(1, null, 2,
                List.of(new GridLayout.Week("N1", 4, 3, null, null)),
                List.of(new GridLayout.Workout("T1", 1, 2)));

        GridRows grid = read("x,Kükk,javascript:alert(1),8,50\nx,Surumine,HTTPS://example.test/a,8,50\n", layout);

        assertNull(grid.drafts().getFirst().exercises().get(0).videoUrl());
        assertEquals("HTTPS://example.test/a", grid.drafts().getFirst().exercises().get(1).videoUrl());
    }

    @Test
    void read_columnsAndRowsThatAreNotInTheSheet_readAsEmpty() {
        GridLayout layout = new GridLayout(1, 40, 41,
                List.of(new GridLayout.Week("N1", 42, 3, 43, 44), new GridLayout.Week("N2", 4, 99, null, null)),
                List.of(new GridLayout.Workout("T1", 1, 500), new GridLayout.Workout("T2", 900, 901)));

        GridRows grid = read("x,Kükk,,8,50\n", layout);

        assertEquals(List.of(new WorkoutDraft("N1 / T1",
                List.of(new DraftExercise("Kükk", null, null, null, List.of(8), null, null)))), grid.drafts());
    }

    @Test
    void read_noWeeksOrNoWorkouts_readsNothing() {
        assertTrue(read(SHEET, new GridLayout(1, 0, 2, List.of(), LAYOUT.workouts())).drafts().isEmpty());
        assertTrue(read(SHEET, new GridLayout(1, 0, 2, LAYOUT.weeks(), List.of())).drafts().isEmpty());
    }

    private static GridLayout oneCell() {
        return new GridLayout(1, null, null,
                List.of(new GridLayout.Week("N1", 4, 3, null, null)),
                List.of(new GridLayout.Workout("T1", 1, 1)));
    }
}
