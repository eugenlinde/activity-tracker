package dev.eugenlinde.activitytracker.importer.csv;

import dev.eugenlinde.activitytracker.importer.llm.TableMapping;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

public final class TableReader {

    private static final BigDecimal MIN_WEIGHT = new BigDecimal("20.0");
    private static final BigDecimal MAX_WEIGHT = new BigDecimal("400.0");
    private static final int MAX_STEPS = 100_000;
    private static final Pattern PLAIN_NUMBER = Pattern.compile("\\d+");
    private static final Pattern SPREADSHEET_WHOLE = Pattern.compile("(\\d+)[.,]0+");
    private static final Pattern GROUPED_THOUSANDS = Pattern.compile("\\d{1,3}(?:[ ,.\\u00a0\\u202f]\\d{3})+");
    private static final Pattern DECIMAL = Pattern.compile("\\d+(?:\\.\\d+)?");

    private TableReader() {
    }

    public static TableRows read(List<List<String>> rows, TableMapping mapping) {
        Map<LocalDate, BigDecimal> weights = new LinkedHashMap<>();
        Map<LocalDate, Integer> steps = new LinkedHashMap<>();
        int unreadable = 0;

        for (List<String> row : rows.subList(Math.min(mapping.headerRows(), rows.size()), rows.size())) {
            String weightCell = cell(row, mapping.weightColumn());
            String stepsCell = cell(row, mapping.stepsColumn());
            if (row.stream().allMatch(String::isBlank)) {
                continue;
            }
            Optional<LocalDate> date = DateReader.read(cell(row, mapping.dateColumn()), mapping.dateOrder());
            if (date.isEmpty()) {
                unreadable++;
                continue;
            }
            if (!weightCell.isBlank()) {
                Optional<BigDecimal> weight = weight(weightCell);
                if (weight.isPresent()) {
                    weights.remove(date.get());
                    weights.put(date.get(), weight.get());
                } else {
                    unreadable++;
                }
            }
            if (!stepsCell.isBlank()) {
                Optional<Integer> count = steps(stepsCell);
                if (count.isPresent()) {
                    steps.remove(date.get());
                    steps.put(date.get(), count.get());
                } else {
                    unreadable++;
                }
            }
        }

        return new TableRows(
                weights.entrySet().stream().map(e -> new WeightRow(e.getKey(), e.getValue())).toList(),
                steps.entrySet().stream().map(e -> new StepsRow(e.getKey(), e.getValue())).toList(),
                unreadable
        );
    }

    private static String cell(List<String> row, Integer column) {
        if (column == null || column >= row.size()) {
            return "";
        }
        return row.get(column).strip();
    }

    private static Optional<BigDecimal> weight(String cell) {
        String number = cell.replaceAll("(?i)kg", "").replace(',', '.').replaceAll("[\\s\\u00a0\\u202f]", "");
        if (!DECIMAL.matcher(number).matches()) {
            return Optional.empty();
        }
        BigDecimal value = new BigDecimal(number).setScale(1, RoundingMode.HALF_UP);
        if (value.compareTo(MIN_WEIGHT) < 0 || value.compareTo(MAX_WEIGHT) > 0) {
            return Optional.empty();
        }
        return Optional.of(value);
    }

    private static Optional<Integer> steps(String cell) {
        String digits;
        var spreadsheetWhole = SPREADSHEET_WHOLE.matcher(cell);
        if (PLAIN_NUMBER.matcher(cell).matches()) {
            digits = cell;
        } else if (GROUPED_THOUSANDS.matcher(cell).matches()) {
            digits = cell.replaceAll("\\D", "");
        } else if (spreadsheetWhole.matches()) {
            digits = spreadsheetWhole.group(1);
        } else {
            return Optional.empty();
        }
        if (digits.length() > 7) {
            return Optional.empty();
        }
        int value = Integer.parseInt(digits);
        return value <= MAX_STEPS ? Optional.of(value) : Optional.empty();
    }
}
