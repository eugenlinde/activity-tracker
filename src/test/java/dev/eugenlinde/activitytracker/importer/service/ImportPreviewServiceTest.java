package dev.eugenlinde.activitytracker.importer.service;

import dev.eugenlinde.activitytracker.activity.ActivityRepository;
import dev.eugenlinde.activitytracker.activity.model.Session;
import dev.eugenlinde.activitytracker.common.EntryDatePolicy;
import dev.eugenlinde.activitytracker.common.exception.EmptyImportFileException;
import dev.eugenlinde.activitytracker.common.exception.ImportLimitExceededException;
import dev.eugenlinde.activitytracker.common.exception.ImportTooLargeException;
import dev.eugenlinde.activitytracker.common.exception.UnsupportedMediaTypeException;
import dev.eugenlinde.activitytracker.importer.ImportFingerprint;
import dev.eugenlinde.activitytracker.importer.ImportProperties;
import dev.eugenlinde.activitytracker.importer.PreviewLimiter;
import dev.eugenlinde.activitytracker.importer.WorkoutDraft;
import dev.eugenlinde.activitytracker.importer.WorkoutDraftMapper;
import dev.eugenlinde.activitytracker.importer.csv.DateOrder;
import dev.eugenlinde.activitytracker.importer.csv.CsvText;
import dev.eugenlinde.activitytracker.importer.dto.ImportPreviewResponse;
import dev.eugenlinde.activitytracker.importer.dto.SkippedEntryPreview;
import dev.eugenlinde.activitytracker.importer.dto.StepsPreview;
import dev.eugenlinde.activitytracker.importer.dto.WeightPreview;
import dev.eugenlinde.activitytracker.importer.llm.FileAnalysis;
import dev.eugenlinde.activitytracker.importer.llm.GridLayout;
import dev.eugenlinde.activitytracker.importer.llm.ImportMappingService;
import dev.eugenlinde.activitytracker.importer.llm.TableMapping;
import dev.eugenlinde.activitytracker.steps.StepEntryRepository;
import dev.eugenlinde.activitytracker.steps.model.StepEntry;
import dev.eugenlinde.activitytracker.weight.WeightEntryRepository;
import dev.eugenlinde.activitytracker.weight.model.WeightEntry;
import jakarta.validation.Validation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ImportPreviewServiceTest {

    private static final Long USER = 7L;
    private static final String TABLE_CSV = """
            Kuupäev;Kaal;Sammud
            01.11.2026;81,3;10 234
            02.11.2026;81,1;9876
            03.11.2026;80,9;
            """;

    private final ImportMappingService mappingService = mock(ImportMappingService.class);
    private final ActivityRepository activityRepository = mock(ActivityRepository.class);
    private final WeightEntryRepository weightEntryRepository = mock(WeightEntryRepository.class);
    private final StepEntryRepository stepEntryRepository = mock(StepEntryRepository.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-17T10:00:00Z"), ZoneOffset.UTC);
    private final ImportProperties properties = new ImportProperties();
    private ImportPreviewService service;

    @BeforeEach
    void setUp() {
        service = new ImportPreviewService(
                mappingService,
                activityRepository,
                weightEntryRepository,
                stepEntryRepository,
                new PreviewLimiter(properties, clock),
                new EntryDatePolicy(clock),
                properties,
                Validation.buildDefaultValidatorFactory().getValidator()
        );
        when(activityRepository.countImportFingerprints(USER)).thenReturn(List.of());
        when(weightEntryRepository.findAllByUserIdAndEntryDateBetweenOrderByEntryDateAsc(eq(USER), any(), any()))
                .thenReturn(List.of());
        when(stepEntryRepository.findAllByUserIdAndEntryDateBetweenOrderByEntryDateAsc(eq(USER), any(), any()))
                .thenReturn(List.of());
    }

    private static byte[] bytes(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }

    private void analysedAsTable() {
        when(mappingService.analyse(anyString()))
                .thenReturn(new FileAnalysis(FileAnalysis.Kind.TABLE, new TableMapping(1, 0, DateOrder.DMY, 1, 2)));
    }

    private static WeightEntry storedWeight(String date, String kg) {
        WeightEntry entry = new WeightEntry();
        entry.setEntryDate(LocalDate.parse(date));
        entry.setWeightKg(new BigDecimal(kg));
        return entry;
    }

    private static StepEntry storedSteps(String date, int steps) {
        StepEntry entry = new StepEntry();
        entry.setEntryDate(LocalDate.parse(date));
        entry.setSteps(steps);
        return entry;
    }

    private static final GridLayout LAYOUT = new GridLayout(1, 0, null,
            List.of(new GridLayout.Week("Nädal 1", 3, 4, null, null), new GridLayout.Week("Nädal 2", 6, 7, null, null)),
            List.of(new GridLayout.Workout("Treening 1", 4, 5)));
    private static final String GRID_CSV = """
            KAVA,,,Nädal 1,,,Nädal 2,
            ,,,,,,,
            ,Treening 1,,Raskus,Kordused,,Raskus,Kordused
            5-8,Kükk,,100,"8, 7",,100,"8, 7"
            5-8,Surumine,,60,3x10,,,
            """;

    private static Session session(double weight, Integer... reps) {
        return WorkoutDraftMapper.toSession(new WorkoutDraft("x", List.of(
                new WorkoutDraft.DraftExercise("Kükk", "5-8", weight, null, List.of(reps), null, null))));
    }

    private static ActivityRepository.FingerprintCount stored(String fingerprint, long total) {
        return new ActivityRepository.FingerprintCount() {
            @Override
            public String getFingerprint() {
                return fingerprint;
            }

            @Override
            public long getTotal() {
                return total;
            }
        };
    }

    @Test
    void preview_table_returnsNewRowsWithNothingStored() {
        analysedAsTable();

        ImportPreviewResponse preview = service.preview(USER, "kaal.csv", bytes(TABLE_CSV));

        assertEquals("TABLE", preview.kind());
        assertEquals(List.of(
                new WeightPreview(LocalDate.parse("2026-11-01"), new BigDecimal("81.3"), null),
                new WeightPreview(LocalDate.parse("2026-11-02"), new BigDecimal("81.1"), null),
                new WeightPreview(LocalDate.parse("2026-11-03"), new BigDecimal("80.9"), null)
        ), preview.weights());
        assertEquals(List.of(
                new StepsPreview(LocalDate.parse("2026-11-01"), 10234, null),
                new StepsPreview(LocalDate.parse("2026-11-02"), 9876, null)
        ), preview.steps());
        assertEquals(0, preview.unchangedWeights());
        assertEquals(0, preview.unchangedSteps());
        assertEquals(0, preview.unreadableRows());
        assertTrue(preview.workouts().isEmpty());
        verify(mappingService, never()).gridLayout(any());
    }

    @Test
    void preview_table_sendsOnlyTheHeadOfTheFileForAnalysis() {
        analysedAsTable();
        properties.setAnalysisRows(2);

        service.preview(USER, "kaal.csv", bytes(TABLE_CSV));

        verify(mappingService).analyse("Kuupäev;Kaal;Sammud\n01.11.2026;81,3;10 234");
    }

    @Test
    void preview_table_leavesOutUnchangedRowsAndShowsTheStoredValueOnChangedOnes() {
        analysedAsTable();
        when(weightEntryRepository.findAllByUserIdAndEntryDateBetweenOrderByEntryDateAsc(
                USER, LocalDate.parse("2026-11-01"), LocalDate.parse("2026-11-03")))
                .thenReturn(List.of(storedWeight("2026-11-01", "81.3"), storedWeight("2026-11-02", "82.0")));
        when(stepEntryRepository.findAllByUserIdAndEntryDateBetweenOrderByEntryDateAsc(
                USER, LocalDate.parse("2026-11-01"), LocalDate.parse("2026-11-02")))
                .thenReturn(List.of(storedSteps("2026-11-01", 10234), storedSteps("2026-11-02", 9000)));

        ImportPreviewResponse preview = service.preview(USER, "kaal.csv", bytes(TABLE_CSV));

        assertEquals(List.of(
                new WeightPreview(LocalDate.parse("2026-11-02"), new BigDecimal("81.1"), new BigDecimal("82.0")),
                new WeightPreview(LocalDate.parse("2026-11-03"), new BigDecimal("80.9"), null)
        ), preview.weights());
        assertEquals(List.of(new StepsPreview(LocalDate.parse("2026-11-02"), 9876, 9000)), preview.steps());
        assertEquals(1, preview.unchangedWeights());
        assertEquals(1, preview.unchangedSteps());
    }

    @Test
    void preview_table_countsFutureDatesAndUnreadableCells() {
        analysedAsTable();
        String csv = "Kuupäev;Kaal;Sammud\n01.11.2026;abc;100\n25.12.2026;80,0;200\nkokku;1;2\n";

        ImportPreviewResponse preview = service.preview(USER, "kaal.csv", bytes(csv));

        assertTrue(preview.weights().isEmpty());
        assertEquals(List.of(new StepsPreview(LocalDate.parse("2026-11-01"), 100, null)), preview.steps());
        assertEquals(4, preview.unreadableRows());
    }

    @Test
    void preview_workoutGrid_asksWhereThingsAreAndReadsTheCellsItself() {
        when(mappingService.analyse(anyString())).thenReturn(new FileAnalysis(FileAnalysis.Kind.WORKOUT_GRID, null));
        List<List<String>> parsed = CsvText.parse(GRID_CSV);
        when(mappingService.gridLayout(parsed)).thenReturn(LAYOUT);

        ImportPreviewResponse preview = service.preview(USER, "plaan.CSV", bytes(GRID_CSV));

        assertEquals(5, parsed.size());
        assertEquals("WORKOUT_GRID", preview.kind());
        assertEquals(List.of("Nädal 1 / Treening 1", "Nädal 2 / Treening 1"),
                preview.workouts().stream().map(workout -> workout.label()).toList());
        assertEquals(session(100.0, 8, 7), preview.workouts().getFirst().details());
        assertEquals(false, preview.workouts().getFirst().alreadyImported());
        assertEquals(1, preview.unreadableRows());
        assertEquals(List.of(new SkippedEntryPreview(5, "Nädal 1 / Treening 1", "Surumine")), preview.skippedEntries());
        verifyNoInteractions(weightEntryRepository, stepEntryRepository);
    }

    @Test
    void preview_workoutGrid_moreWorkoutsThanOneConfirmCanTake_isTooLarge() {
        when(mappingService.analyse(anyString())).thenReturn(new FileAnalysis(FileAnalysis.Kind.WORKOUT_GRID, null));
        List<GridLayout.Workout> workouts = new ArrayList<>();
        StringBuilder csv = new StringBuilder();
        for (int i = 1; i <= 201; i++) {
            workouts.add(new GridLayout.Workout("T" + i, i, i));
            csv.append("Kükk,5\n");
        }
        when(mappingService.gridLayout(any())).thenReturn(new GridLayout(0, null, null,
                List.of(new GridLayout.Week("N1", null, 1, null, null)), workouts));

        assertThrows(ImportTooLargeException.class, () -> service.preview(USER, "plaan.csv", bytes(csv.toString())));
    }

    @Test
    void preview_workoutGrid_marksAlreadyImportedByCount() {
        when(mappingService.analyse(anyString())).thenReturn(new FileAnalysis(FileAnalysis.Kind.WORKOUT_GRID, null));
        when(mappingService.gridLayout(any())).thenReturn(new GridLayout(0, null, null,
                List.of(new GridLayout.Week("N1", 1, 2, null, null), new GridLayout.Week("N2", 3, 4, null, null),
                        new GridLayout.Week("N3", 5, 6, null, null), new GridLayout.Week("N4", 7, 8, null, null)),
                List.of(new GridLayout.Workout("T1", 1, 1))));
        when(activityRepository.countImportFingerprints(USER))
                .thenReturn(List.of(stored(ImportFingerprint.of(session(100.0, 8, 7)), 2), stored("someoneelse", 5)));

        ImportPreviewResponse preview = service.preview(USER, "plaan.csv",
                bytes("Kükk,100,\"8, 7\",100,\"8, 7\",102.5,\"8, 7\",100,\"8, 7\"\n"));

        List<Boolean> marks = new ArrayList<>();
        preview.workouts().forEach(workout -> marks.add(workout.alreadyImported()));
        assertEquals(List.of(true, true, false, false), marks);
    }

    @Test
    void preview_workoutGrid_tooManyExercisesForAStoredWorkout_isRefusedWithItsLabel() {
        when(mappingService.analyse(anyString())).thenReturn(new FileAnalysis(FileAnalysis.Kind.WORKOUT_GRID, null));
        when(mappingService.gridLayout(any())).thenReturn(new GridLayout(0, null, null,
                List.of(new GridLayout.Week("N1", null, 1, null, null)),
                List.of(new GridLayout.Workout("T1", 1, 13))));
        StringBuilder csv = new StringBuilder();
        for (int i = 0; i < 13; i++) {
            csv.append("Harjutus ").append(i).append(",5\n");
        }

        ImportTooLargeException refusal = assertThrows(ImportTooLargeException.class,
                () -> service.preview(USER, "plaan.csv", bytes(csv.toString())));
        assertTrue(refusal.getMessage().contains("N1 / T1"), refusal.getMessage());
    }

    @Test
    void preview_workoutGrid_sheetWithoutTheGridLayout_returnsNothingToImport() {
        when(mappingService.analyse(anyString())).thenReturn(new FileAnalysis(FileAnalysis.Kind.WORKOUT_GRID, null));
        when(mappingService.gridLayout(any())).thenReturn(new GridLayout(0, null, null, List.of(), List.of()));

        ImportPreviewResponse preview = service.preview(USER, "plaan.csv", bytes(GRID_CSV));

        assertEquals("UNKNOWN", preview.kind());
        assertTrue(preview.workouts().isEmpty());
    }

    @Test
    void preview_unknownKind_returnsNothingToImport() {
        when(mappingService.analyse(anyString())).thenReturn(new FileAnalysis(FileAnalysis.Kind.UNKNOWN, null));

        ImportPreviewResponse preview = service.preview(USER, "x.csv", bytes("tere,maailm\n"));

        assertEquals("UNKNOWN", preview.kind());
        assertTrue(preview.workouts().isEmpty() && preview.weights().isEmpty() && preview.steps().isEmpty());
    }

    @Test
    void preview_notACsvName_isRefusedBeforeTheModelIsAsked() {
        assertThrows(UnsupportedMediaTypeException.class, () -> service.preview(USER, "plaan.xlsx", bytes("a,b\n")));
        assertThrows(UnsupportedMediaTypeException.class, () -> service.preview(USER, null, bytes("a,b\n")));
        verifyNoInteractions(mappingService);
    }

    @Test
    void preview_binaryContentWithCsvName_isRefused() {
        byte[] zipLike = {'P', 'K', 3, 4, 0, 0, 0, 0, 'a', ','};

        assertThrows(UnsupportedMediaTypeException.class, () -> service.preview(USER, "plaan.csv", zipLike));
        verifyNoInteractions(mappingService);
    }

    @Test
    void preview_emptyOrBlankFile_isRefused() {
        assertThrows(EmptyImportFileException.class, () -> service.preview(USER, "x.csv", new byte[0]));
        assertThrows(EmptyImportFileException.class, () -> service.preview(USER, "x.csv", bytes("\n\n  \n")));
        verifyNoInteractions(mappingService);
    }

    @Test
    void preview_textOverTheCap_isRefusedBeforeTheModelIsAsked() {
        properties.setMaxTextBytes(20);

        assertThrows(ImportTooLargeException.class, () -> service.preview(USER, "x.csv", bytes(TABLE_CSV)));
        verifyNoInteractions(mappingService);
    }

    @Test
    void preview_overTheDailyLimit_isRefusedBeforeTheModelIsAsked() {
        properties.setDailyPreviewLimit(1);
        analysedAsTable();
        service.preview(USER, "kaal.csv", bytes(TABLE_CSV));

        assertThrows(ImportLimitExceededException.class, () -> service.preview(USER, "kaal.csv", bytes(TABLE_CSV)));
        verify(mappingService).analyse(anyString());
    }

    @Test
    void preview_refusedFiles_doNotUseUpTheDailyLimit() {
        properties.setDailyPreviewLimit(1);
        analysedAsTable();
        assertThrows(UnsupportedMediaTypeException.class, () -> service.preview(USER, "plaan.xlsx", bytes("a,b\n")));
        assertThrows(EmptyImportFileException.class, () -> service.preview(USER, "x.csv", new byte[0]));

        assertEquals("TABLE", service.preview(USER, "kaal.csv", bytes(TABLE_CSV)).kind());
    }

    @Test
    void preview_headSentForAnalysis_hasNoBlankLines_soRowCountsMatchWhatJavaReads() {
        analysedAsTable();
        properties.setAnalysisRows(3);

        service.preview(USER, "kaal.csv", bytes("Minu kaal\n\n\nKuupäev;Kaal\n01.11.2026;81,3\n"));

        verify(mappingService).analyse("Minu kaal\nKuupäev;Kaal\n01.11.2026;81,3");
    }

    @Test
    void preview_doesNotHoldADatabaseTransactionWhileTheModelIsAsked() throws NoSuchMethodException {
        assertEquals(null, ImportPreviewService.class
                .getMethod("preview", Long.class, String.class, byte[].class)
                .getAnnotation(org.springframework.transaction.annotation.Transactional.class));
        assertEquals(null, ImportPreviewService.class
                .getAnnotation(org.springframework.transaction.annotation.Transactional.class));
    }
}
