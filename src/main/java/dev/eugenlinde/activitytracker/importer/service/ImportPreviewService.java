package dev.eugenlinde.activitytracker.importer.service;

import dev.eugenlinde.activitytracker.activity.ActivityRepository;
import dev.eugenlinde.activitytracker.activity.model.Session;
import dev.eugenlinde.activitytracker.common.EntryDatePolicy;
import dev.eugenlinde.activitytracker.common.exception.EmptyImportFileException;
import dev.eugenlinde.activitytracker.common.exception.ImportTooLargeException;
import dev.eugenlinde.activitytracker.common.exception.InvalidEntryDateException;
import dev.eugenlinde.activitytracker.common.exception.UnsupportedMediaTypeException;
import dev.eugenlinde.activitytracker.importer.ImportFingerprint;
import dev.eugenlinde.activitytracker.importer.ImportProperties;
import dev.eugenlinde.activitytracker.importer.PreviewLimiter;
import dev.eugenlinde.activitytracker.importer.WorkoutDraft;
import dev.eugenlinde.activitytracker.importer.WorkoutDraftMapper;
import dev.eugenlinde.activitytracker.importer.csv.CsvText;
import dev.eugenlinde.activitytracker.importer.csv.DayGridReader;
import dev.eugenlinde.activitytracker.importer.csv.DayGridRows;
import dev.eugenlinde.activitytracker.importer.csv.GridReader;
import dev.eugenlinde.activitytracker.importer.csv.GridRows;
import dev.eugenlinde.activitytracker.importer.csv.StepsRow;
import dev.eugenlinde.activitytracker.importer.csv.TableReader;
import dev.eugenlinde.activitytracker.importer.csv.TableRows;
import dev.eugenlinde.activitytracker.importer.csv.WeightRow;
import dev.eugenlinde.activitytracker.importer.dto.ImportPreviewResponse;
import dev.eugenlinde.activitytracker.importer.dto.SkippedEntryPreview;
import dev.eugenlinde.activitytracker.importer.dto.StepsPreview;
import dev.eugenlinde.activitytracker.importer.dto.WeightPreview;
import dev.eugenlinde.activitytracker.importer.dto.WorkoutPreview;
import dev.eugenlinde.activitytracker.importer.llm.DayGridMapping;
import dev.eugenlinde.activitytracker.importer.llm.FileAnalysis;
import dev.eugenlinde.activitytracker.importer.llm.GridLayout;
import dev.eugenlinde.activitytracker.importer.llm.ImportMappingService;
import dev.eugenlinde.activitytracker.steps.StepEntryRepository;
import dev.eugenlinde.activitytracker.steps.model.StepEntry;
import dev.eugenlinde.activitytracker.weight.WeightEntryRepository;
import dev.eugenlinde.activitytracker.weight.model.WeightEntry;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class ImportPreviewService {

    private static final String CSV_EXTENSION = ".csv";
    private static final int MAX_WORKOUTS = 200;
    private static final int EARLIEST_YEAR = 2000;
    private static final int LATEST_YEAR = 2100;
    private static final Pattern YEAR_IN_NAME = Pattern.compile("(?<!\\d)(20\\d{2})(?!\\d)");

    private final ImportMappingService importMappingService;
    private final ActivityRepository activityRepository;
    private final WeightEntryRepository weightEntryRepository;
    private final StepEntryRepository stepEntryRepository;
    private final PreviewLimiter previewLimiter;
    private final EntryDatePolicy entryDatePolicy;
    private final ImportProperties importProperties;
    private final Validator validator;

    public ImportPreviewService(
            ImportMappingService importMappingService,
            ActivityRepository activityRepository,
            WeightEntryRepository weightEntryRepository,
            StepEntryRepository stepEntryRepository,
            PreviewLimiter previewLimiter,
            EntryDatePolicy entryDatePolicy,
            ImportProperties importProperties,
            Validator validator
    ) {
        this.importMappingService = importMappingService;
        this.activityRepository = activityRepository;
        this.weightEntryRepository = weightEntryRepository;
        this.stepEntryRepository = stepEntryRepository;
        this.previewLimiter = previewLimiter;
        this.entryDatePolicy = entryDatePolicy;
        this.importProperties = importProperties;
        this.validator = validator;
    }

    public ImportPreviewResponse preview(Long userId, String filename, byte[] bytes, Integer year) {
        if (year != null && (year < EARLIEST_YEAR || year > LATEST_YEAR)) {
            throw new InvalidEntryDateException();
        }
        if (filename == null || !filename.toLowerCase(Locale.ROOT).endsWith(CSV_EXTENSION)) {
            throw new UnsupportedMediaTypeException("Only .csv files can be imported. Save the sheet as CSV first.");
        }
        if (bytes.length == 0) {
            throw new EmptyImportFileException("The file is empty.");
        }
        if (looksBinary(bytes)) {
            throw new UnsupportedMediaTypeException("This is not a text CSV file. Save the sheet as CSV first.");
        }
        String text = CsvText.decode(bytes);
        if (text.getBytes(StandardCharsets.UTF_8).length > importProperties.getMaxTextBytes()) {
            throw new ImportTooLargeException("The file is too large to read: the limit is "
                    + importProperties.getMaxTextBytes() / 1000 + " kB of text.");
        }
        List<List<String>> rows = CsvText.parse(text);
        if (rows.stream().allMatch(row -> row.stream().allMatch(String::isBlank))) {
            throw new EmptyImportFileException("The file is empty.");
        }
        previewLimiter.acquire(userId);

        FileAnalysis analysis = importMappingService.analyse(head(text));
        return switch (analysis.kind()) {
            case TABLE -> tablePreview(userId, TableReader.read(rows, analysis.table()), null);
            case DAY_GRID -> dayGridPreview(userId, filename, rows, analysis.dayGrid(), year);
            case WORKOUT_GRID -> workoutPreview(userId, rows);
            case UNKNOWN -> nothingToImport();
        };
    }

    private static boolean looksBinary(byte[] bytes) {
        for (byte value : bytes) {
            if (value == 0) {
                return true;
            }
        }
        return false;
    }

    private String head(String text) {
        return text.lines()
                .filter(line -> !line.isBlank())
                .limit(importProperties.getAnalysisRows())
                .collect(Collectors.joining("\n"));
    }

    private ImportPreviewResponse workoutPreview(Long userId, List<List<String>> rows) {
        GridLayout layout = importMappingService.gridLayout(rows);
        if (layout.weeks().isEmpty() || layout.workouts().isEmpty()) {
            return nothingToImport();
        }
        GridRows grid = GridReader.read(rows, layout);
        if (grid.drafts().size() > MAX_WORKOUTS) {
            throw new ImportTooLargeException("This file holds more than " + MAX_WORKOUTS
                    + " workouts. Split it into smaller files.");
        }
        Map<String, Long> remaining = new HashMap<>();
        activityRepository.countImportFingerprints(userId)
                .forEach(count -> remaining.put(count.getFingerprint(), count.getTotal()));

        List<WorkoutPreview> workouts = new ArrayList<>();
        for (WorkoutDraft draft : grid.drafts()) {
            Session session = WorkoutDraftMapper.toSession(draft);
            Set<ConstraintViolation<Session>> violations = validator.validate(session);
            if (!violations.isEmpty()) {
                throw new ImportTooLargeException("\"" + draft.label()
                        + "\" has more exercises than the app can store in one workout.");
            }
            String fingerprint = ImportFingerprint.of(session);
            long left = remaining.getOrDefault(fingerprint, 0L);
            boolean alreadyImported = left > 0;
            if (alreadyImported) {
                remaining.put(fingerprint, left - 1);
            }
            workouts.add(new WorkoutPreview(draft.label(), session, alreadyImported));
        }
        List<SkippedEntryPreview> skipped = grid.skipped().stream()
                .map(entry -> new SkippedEntryPreview(entry.row(), entry.workout(), entry.exercise()))
                .toList();
        return new ImportPreviewResponse(
                FileAnalysis.Kind.WORKOUT_GRID.name(), workouts, List.of(), List.of(), 0, 0, skipped.size(), skipped, null);
    }

    private static ImportPreviewResponse nothingToImport() {
        return new ImportPreviewResponse(
                FileAnalysis.Kind.UNKNOWN.name(), List.of(), List.of(), List.of(), 0, 0, 0, List.of(), null);
    }

    private ImportPreviewResponse dayGridPreview(
            Long userId, String filename, List<List<String>> rows, DayGridMapping mapping, Integer year
    ) {
        Integer chosen = year != null ? year : yearInName(filename);
        DayGridRows grid = DayGridReader.read(rows, mapping, chosen, entryDatePolicy.today());
        return tablePreview(userId, grid.table(), grid.assumedYear());
    }

    private static Integer yearInName(String filename) {
        Set<Integer> years = new HashSet<>();
        Matcher matcher = YEAR_IN_NAME.matcher(filename);
        while (matcher.find()) {
            years.add(Integer.valueOf(matcher.group(1)));
        }
        return years.size() == 1 ? years.iterator().next() : null;
    }

    private ImportPreviewResponse tablePreview(Long userId, TableRows table, Integer assumedYear) {
        LocalDate latest = entryDatePolicy.latestAllowedDate();
        int unreadable = table.unreadable();

        List<WeightRow> weightRows = table.weights().stream().filter(row -> !row.date().isAfter(latest)).toList();
        List<StepsRow> stepsRows = table.steps().stream().filter(row -> !row.date().isAfter(latest)).toList();
        unreadable += table.weights().size() - weightRows.size();
        unreadable += table.steps().size() - stepsRows.size();

        Map<LocalDate, BigDecimal> storedWeights = new HashMap<>();
        if (!weightRows.isEmpty()) {
            LocalDate from = weightRows.stream().map(WeightRow::date).min(LocalDate::compareTo).orElseThrow();
            LocalDate to = weightRows.stream().map(WeightRow::date).max(LocalDate::compareTo).orElseThrow();
            for (WeightEntry entry : weightEntryRepository
                    .findAllByUserIdAndEntryDateBetweenOrderByEntryDateAsc(userId, from, to)) {
                storedWeights.put(entry.getEntryDate(), entry.getWeightKg());
            }
        }
        Map<LocalDate, Integer> storedSteps = new HashMap<>();
        if (!stepsRows.isEmpty()) {
            LocalDate from = stepsRows.stream().map(StepsRow::date).min(LocalDate::compareTo).orElseThrow();
            LocalDate to = stepsRows.stream().map(StepsRow::date).max(LocalDate::compareTo).orElseThrow();
            for (StepEntry entry : stepEntryRepository
                    .findAllByUserIdAndEntryDateBetweenOrderByEntryDateAsc(userId, from, to)) {
                storedSteps.put(entry.getEntryDate(), entry.getSteps());
            }
        }

        List<WeightPreview> weights = new ArrayList<>();
        int unchangedWeights = 0;
        for (WeightRow row : weightRows) {
            BigDecimal stored = storedWeights.get(row.date());
            if (stored != null && stored.compareTo(row.weightKg()) == 0) {
                unchangedWeights++;
            } else {
                weights.add(new WeightPreview(row.date(), row.weightKg(), stored));
            }
        }
        List<StepsPreview> steps = new ArrayList<>();
        int unchangedSteps = 0;
        for (StepsRow row : stepsRows) {
            Integer stored = storedSteps.get(row.date());
            if (stored != null && stored == row.steps()) {
                unchangedSteps++;
            } else {
                steps.add(new StepsPreview(row.date(), row.steps(), stored));
            }
        }
        return new ImportPreviewResponse(
                FileAnalysis.Kind.TABLE.name(), List.of(), weights, steps, unchangedWeights, unchangedSteps, unreadable,
                List.of(), assumedYear);
    }
}
