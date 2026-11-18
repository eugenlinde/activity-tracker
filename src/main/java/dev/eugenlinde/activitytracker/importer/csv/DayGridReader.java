package dev.eugenlinde.activitytracker.importer.csv;

import dev.eugenlinde.activitytracker.importer.llm.DayGridMapping;

import java.math.BigDecimal;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class DayGridReader {

    private static final Pattern DAY_AND_MONTH = Pattern.compile("^\\s*(\\d{1,2})[./\\-](\\d{1,2})\\.?\\s*$");
    private static final int MONTHS_BACK_THAT_MEAN_A_NEW_YEAR = 6;
    private static final int COMMON_YEAR = 2023;
    private static final int LEAP_YEAR = 2024;

    private DayGridReader() {
    }

    public static DayGridRows read(List<List<String>> rows, DayGridMapping mapping, Integer year, LocalDate today) {
        List<Block> blocks = blocks(rows, mapping);
        Set<DateOrder> orders = new LinkedHashSet<>(List.of(mapping.dateOrder(), DateOrder.DMY, DateOrder.MDY, DateOrder.YMD));
        Attempt best = null;
        for (DateOrder order : orders) {
            Attempt attempt = attempt(blocks, mapping.labelColumn(), order, year, today);
            if (best == null || attempt.daysInWellFormedWeeks() > best.daysInWellFormedWeeks()) {
                best = attempt;
            }
        }
        return best.rows();
    }

    private static List<Block> blocks(List<List<String>> rows, DayGridMapping mapping) {
        String dateLabel = GridLayoutChecks.normalise(mapping.dateLabel());
        String weightLabel = mapping.weightLabel() == null ? null : GridLayoutChecks.normalise(mapping.weightLabel());
        String stepsLabel = mapping.stepsLabel() == null ? null : GridLayoutChecks.normalise(mapping.stepsLabel());
        List<Block> blocks = new ArrayList<>();
        Block current = null;
        for (List<String> row : rows) {
            if (mapping.labelColumn() >= row.size()) {
                continue;
            }
            String label = GridLayoutChecks.normalise(row.get(mapping.labelColumn()));
            if (label.equals(dateLabel)) {
                current = new Block(row);
                blocks.add(current);
            } else if (current != null && label.equals(weightLabel) && current.weightRow == null) {
                current.weightRow = row;
            } else if (current != null && label.equals(stepsLabel) && current.stepsRow == null) {
                current.stepsRow = row;
            }
        }
        return blocks;
    }

    private static Attempt attempt(List<Block> blocks, int labelColumn, DateOrder order, Integer year, LocalDate today) {
        List<List<Day>> days = new ArrayList<>();
        List<Boolean> wellFormed = new ArrayList<>();
        for (Block block : blocks) {
            List<Day> parsed = new ArrayList<>();
            for (int column = 0; column < block.dateRow.size(); column++) {
                if (column != labelColumn) {
                    day(block.dateRow.get(column), order, column).ifPresent(parsed::add);
                }
            }
            days.add(parsed);
            wellFormed.add(wellFormed(parsed));
        }

        int yearsOn = 0;
        Day previous = null;
        Day lastFilled = null;
        boolean anyWithoutYear = false;
        for (int i = 0; i < blocks.size(); i++) {
            if (!wellFormed.get(i)) {
                continue;
            }
            for (Day day : days.get(i)) {
                if (day.year != null) {
                    continue;
                }
                anyWithoutYear = true;
                if (previous != null && previous.month - day.month >= MONTHS_BACK_THAT_MEAN_A_NEW_YEAR) {
                    yearsOn++;
                }
                day.yearsOn = yearsOn;
                previous = day;
                if (filled(blocks.get(i).weightRow, day.column) || filled(blocks.get(i).stepsRow, day.column)) {
                    lastFilled = day;
                }
            }
        }
        Integer assumedYear = !anyWithoutYear ? null : year != null ? year : latestYearNotInTheFuture(lastFilled, today);

        Map<LocalDate, BigDecimal> weights = new LinkedHashMap<>();
        Map<LocalDate, Integer> steps = new LinkedHashMap<>();
        int unreadable = 0;
        int daysInWellFormedWeeks = 0;
        for (int i = 0; i < blocks.size(); i++) {
            Block block = blocks.get(i);
            List<Day> blockDays = days.get(i);
            Function<Day, LocalDate> date = day -> date(day.year != null ? day.year : assumedYear + day.yearsOn, day);
            if (wellFormed.get(i)) {
                daysInWellFormedWeeks += blockDays.size();
            }
            if (!wellFormed.get(i) || !followEachOther(blockDays, date)) {
                for (Day day : blockDays) {
                    unreadable += (filled(block.weightRow, day.column) ? 1 : 0) + (filled(block.stepsRow, day.column) ? 1 : 0);
                }
                continue;
            }
            for (Day day : blockDays) {
                LocalDate when = date.apply(day);
                if (filled(block.weightRow, day.column)) {
                    Optional<BigDecimal> weight = TableReader.weight(block.weightRow.get(day.column).strip());
                    if (weight.isPresent()) {
                        weights.remove(when);
                        weights.put(when, weight.get());
                    } else {
                        unreadable++;
                    }
                }
                if (filled(block.stepsRow, day.column)) {
                    Optional<Integer> count = TableReader.steps(block.stepsRow.get(day.column).strip());
                    if (count.isPresent()) {
                        steps.remove(when);
                        steps.put(when, count.get());
                    } else {
                        unreadable++;
                    }
                }
            }
        }
        TableRows table = new TableRows(
                weights.entrySet().stream().map(entry -> new WeightRow(entry.getKey(), entry.getValue())).toList(),
                steps.entrySet().stream().map(entry -> new StepsRow(entry.getKey(), entry.getValue())).toList(),
                unreadable
        );
        return new Attempt(new DayGridRows(table, weights.isEmpty() && steps.isEmpty() && unreadable == 0 ? null : assumedYear),
                daysInWellFormedWeeks);
    }

    private static Optional<Day> day(String cell, DateOrder order, int column) {
        Optional<LocalDate> full = DateReader.read(cell, order);
        if (full.isPresent()) {
            return Optional.of(new Day(column, full.get().getDayOfMonth(), full.get().getMonthValue(), full.get().getYear()));
        }
        Matcher matcher = DAY_AND_MONTH.matcher(cell);
        if (!matcher.matches()) {
            return Optional.empty();
        }
        int first = Integer.parseInt(matcher.group(1));
        int second = Integer.parseInt(matcher.group(2));
        Day day = order == DateOrder.DMY ? new Day(column, first, second, null) : new Day(column, second, first, null);
        return date(LEAP_YEAR, day) == null ? Optional.empty() : Optional.of(day);
    }

    private static boolean wellFormed(List<Day> days) {
        if (days.isEmpty()) {
            return false;
        }
        boolean allWithYear = days.stream().allMatch(day -> day.year != null);
        boolean noneWithYear = days.stream().allMatch(day -> day.year == null);
        if (allWithYear) {
            return followEachOther(days, day -> date(day.year, day));
        }
        return noneWithYear && (followEachOther(days, inYear(COMMON_YEAR, days)) || followEachOther(days, inYear(LEAP_YEAR, days)));
    }

    private static Function<Day, LocalDate> inYear(int year, List<Day> days) {
        Map<Day, Integer> yearsOn = new LinkedHashMap<>();
        int on = 0;
        Day previous = null;
        for (Day day : days) {
            if (previous != null && previous.month - day.month >= MONTHS_BACK_THAT_MEAN_A_NEW_YEAR) {
                on++;
            }
            yearsOn.put(day, on);
            previous = day;
        }
        return day -> date(year + yearsOn.get(day), day);
    }

    private static boolean followEachOther(List<Day> days, Function<Day, LocalDate> date) {
        Day previous = null;
        LocalDate previousDate = null;
        for (Day day : days) {
            LocalDate current = date.apply(day);
            if (current == null) {
                return false;
            }
            if (previous != null && ChronoUnit.DAYS.between(previousDate, current) != day.column - previous.column) {
                return false;
            }
            previous = day;
            previousDate = current;
        }
        return !days.isEmpty();
    }

    private static int latestYearNotInTheFuture(Day lastFilled, LocalDate today) {
        if (lastFilled == null) {
            return today.getYear();
        }
        int year = today.getYear() - lastFilled.yearsOn;
        LocalDate date = date(year + lastFilled.yearsOn, lastFilled);
        return date != null && date.isAfter(today) ? year - 1 : year;
    }

    private static LocalDate date(int year, Day day) {
        try {
            return LocalDate.of(year, day.month, day.day);
        } catch (DateTimeException notADate) {
            return null;
        }
    }

    private static boolean filled(List<String> row, int column) {
        return row != null && column < row.size() && !row.get(column).isBlank();
    }

    private record Attempt(DayGridRows rows, int daysInWellFormedWeeks) {
    }

    private static final class Block {
        private final List<String> dateRow;
        private List<String> weightRow;
        private List<String> stepsRow;

        private Block(List<String> dateRow) {
            this.dateRow = dateRow;
        }
    }

    private static final class Day {
        private final int column;
        private final int day;
        private final int month;
        private final Integer year;
        private int yearsOn;

        private Day(int column, int day, int month, Integer year) {
            this.column = column;
            this.day = day;
            this.month = month;
            this.year = year;
        }
    }
}
