package dev.eugenlinde.activitytracker.importer.service;

import dev.eugenlinde.activitytracker.activity.ActivityRepository;
import dev.eugenlinde.activitytracker.activity.model.Session;
import dev.eugenlinde.activitytracker.common.EntryDatePolicy;
import dev.eugenlinde.activitytracker.common.exception.EmptyImportFileException;
import dev.eugenlinde.activitytracker.common.exception.ImportMappingException;
import dev.eugenlinde.activitytracker.common.exception.ImportTooLargeException;
import dev.eugenlinde.activitytracker.common.exception.UnsupportedMediaTypeException;
import dev.eugenlinde.activitytracker.importer.ImportFingerprint;
import dev.eugenlinde.activitytracker.importer.ImportProperties;
import dev.eugenlinde.activitytracker.importer.PreviewLimiter;
import dev.eugenlinde.activitytracker.importer.WorkoutDraftMapper;
import dev.eugenlinde.activitytracker.importer.csv.CsvText;
import dev.eugenlinde.activitytracker.importer.csv.StepsRow;
import dev.eugenlinde.activitytracker.importer.csv.TableReader;
import dev.eugenlinde.activitytracker.importer.csv.TableRows;
import dev.eugenlinde.activitytracker.importer.csv.WeightRow;
import dev.eugenlinde.activitytracker.importer.dto.ImportPreviewResponse;
import dev.eugenlinde.activitytracker.importer.dto.StepsPreview;
import dev.eugenlinde.activitytracker.importer.dto.WeightPreview;
import dev.eugenlinde.activitytracker.importer.dto.WorkoutPreview;
import dev.eugenlinde.activitytracker.importer.llm.FileAnalysis;
import dev.eugenlinde.activitytracker.importer.llm.ImportMappingService;
import dev.eugenlinde.activitytracker.importer.llm.TableMapping;
import dev.eugenlinde.activitytracker.importer.llm.WorkoutExtraction;
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
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class ImportPreviewService {

    private static final String CSV_EXTENSION = ".csv";

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

    public ImportPreviewResponse preview(Long userId, String filename, byte[] bytes) {
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
            case TABLE -> tablePreview(userId, rows, analysis.table());
            case WORKOUT_GRID -> workoutPreview(userId, text);
            case UNKNOWN -> new ImportPreviewResponse(analysis.kind().name(), List.of(), List.of(), List.of(), 0, 0, 0);
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

    private ImportPreviewResponse workoutPreview(Long userId, String text) {
        WorkoutExtraction extraction = importMappingService.extractWorkouts(text);
        Map<String, Long> remaining = new HashMap<>();
        activityRepository.countImportFingerprints(userId)
                .forEach(count -> remaining.put(count.getFingerprint(), count.getTotal()));

        List<WorkoutPreview> workouts = new ArrayList<>();
        for (WorkoutExtraction.Draft draft : extraction.drafts()) {
            Session session = WorkoutDraftMapper.toSession(draft);
            Set<ConstraintViolation<Session>> violations = validator.validate(session);
            if (!violations.isEmpty()) {
                throw new ImportMappingException(
                        "Workout number " + (workouts.size() + 1) + " does not fit what the app can store");
            }
            String fingerprint = ImportFingerprint.of(session);
            long left = remaining.getOrDefault(fingerprint, 0L);
            boolean alreadyImported = left > 0;
            if (alreadyImported) {
                remaining.put(fingerprint, left - 1);
            }
            workouts.add(new WorkoutPreview(draft.label(), draft.notes(), session, alreadyImported));
        }
        return new ImportPreviewResponse(FileAnalysis.Kind.WORKOUT_GRID.name(), workouts, List.of(), List.of(), 0, 0, 0);
    }

    private ImportPreviewResponse tablePreview(Long userId, List<List<String>> rows, TableMapping mapping) {
        TableRows table = TableReader.read(rows, mapping);
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
                FileAnalysis.Kind.TABLE.name(), List.of(), weights, steps, unchangedWeights, unchangedSteps, unreadable);
    }
}
