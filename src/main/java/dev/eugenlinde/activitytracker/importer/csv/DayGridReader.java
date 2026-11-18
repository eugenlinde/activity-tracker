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
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class DayGridReader {

    private static final Pattern DAY_AND_MONTH = Pattern.compile("^\\s*(\\d{1,2})[./\\-](\\d{1,2})\\.?\\s*$");
    private static final int MONTHS_BACK_THAT_MEAN_A_NEW_YEAR = 6;
    private static final int LONGEST_GAP_BETWEEN_WEEKS_IN_DAYS = 62;
    private static final int COMMON_YEAR = 2023;
    private static final int LEAP_YEAR = 2024;
    private static final int DAYS_IN_LEAP_YEAR = 366;

    private DayGridReader() {
    }

    public static DayGridRows read(
            List<List<String>> rows, DayGridMapping mapping, Integer year, Integer yearHint, LocalDate today
    ) {
        Set<DateOrder> orders = new LinkedHashSet<>(List.of(mapping.dateOrder(), DateOrder.DMY, DateOrder.MDY, DateOrder.YMD));
        Attempt best = null;
        for (DateOrder order : orders) {
            Attempt attempt = attempt(weeks(rows, mapping, order), year, yearHint, today);
            if (best == null || attempt.daysInWellFormedWeeks() > best.daysInWellFormedWeeks()) {
                best = attempt;
            }
        }
        return best.rows();
    }

    private static List<Week> weeks(List<List<String>> rows, DayGridMapping mapping, DateOrder order) {
        String dateLabel = label(mapping.dateLabel());
        String weightLabel = mapping.weightLabel() == null ? null : label(mapping.weightLabel());
        String stepsLabel = mapping.stepsLabel() == null ? null : label(mapping.stepsLabel());
        List<Week> weeks = new ArrayList<>();
        Week current = null;
        for (List<String> row : rows) {
            String label = mapping.labelColumn() < row.size() ? label(row.get(mapping.labelColumn())) : "";
            if (label.equals(dateLabel)) {
                current = new Week(row, mapping.labelColumn(), order);
                weeks.add(current);
            } else if (label.isEmpty()) {
                current = null;
            } else if (current != null && label.equals(weightLabel) && current.weightRow == null) {
                current.weightRow = row;
            } else if (current != null && label.equals(stepsLabel) && current.stepsRow == null) {
                current.stepsRow = row;
            }
        }
        return weeks;
    }

    private static String label(String text) {
        return GridLayoutChecks.normalise(text).toLowerCase(Locale.ROOT);
    }

    private static Attempt attempt(List<Week> weeks, Integer year, Integer yearHint, LocalDate today) {
        List<Week> withoutYear = weeks.stream().filter(week -> week.wellFormed && !week.hasYears).toList();
        List<Week> inOrder = newestFirst(withoutYear) ? withoutYear.reversed() : withoutYear;

        List<Week> placed = new ArrayList<>();
        Day last = null;
        for (Week week : inOrder) {
            Day first = week.days.getFirst();
            int yearsOn = 0;
            if (last != null) {
                yearsOn = isLaterInTheYear(first, last) ? last.yearsOn : last.yearsOn + 1;
                int gap = (yearsOn - last.yearsOn) * DAYS_IN_LEAP_YEAR + dayOfYear(first) - dayOfYear(last);
                if (gap > LONGEST_GAP_BETWEEN_WEEKS_IN_DAYS) {
                    continue;
                }
            }
            Day previous = null;
            for (Day day : week.days) {
                if (previous != null && previous.month - day.month >= MONTHS_BACK_THAT_MEAN_A_NEW_YEAR) {
                    yearsOn++;
                }
                day.yearsOn = yearsOn;
                previous = day;
            }
            week.placed = true;
            placed.add(week);
            last = previous;
        }

        Integer assumedYear = null;
        if (!placed.isEmpty()) {
            int mostDays = yearsOnWithMostDays(placed);
            Day lastFilled = lastDayWithAValue(placed);
            Function<Integer, Boolean> notInTheFuture = candidate -> {
                if (lastFilled == null) {
                    return true;
                }
                LocalDate date = date(candidate + lastFilled.yearsOn - mostDays, lastFilled);
                return date == null || !date.isAfter(today);
            };
            if (year != null) {
                assumedYear = year;
            } else if (yearHint != null && notInTheFuture.apply(yearHint)) {
                assumedYear = yearHint;
            } else {
                int guess = today.getYear() - (lastFilled == null ? 0 : lastFilled.yearsOn - mostDays);
                assumedYear = notInTheFuture.apply(guess) ? guess : guess - 1;
            }
            for (Week week : placed) {
                for (Day day : week.days) {
                    day.year = assumedYear + day.yearsOn - mostDays;
                }
            }
        }

        Map<LocalDate, BigDecimal> weights = new LinkedHashMap<>();
        Map<LocalDate, Integer> steps = new LinkedHashMap<>();
        int unreadable = 0;
        int daysInWellFormedWeeks = 0;
        boolean yearWasNeeded = false;
        for (Week week : weeks) {
            if (week.wellFormed) {
                daysInWellFormedWeeks += week.days.size();
            }
            boolean readable = week.wellFormed && (week.hasYears || week.placed)
                    && followEachOther(week.days, day -> date(day.year, day));
            if (!readable) {
                unreadable += week.valuesUnder(week.dateShapedColumns.isEmpty() ? week.otherColumns : week.dateShapedColumns);
                continue;
            }
            int firstColumn = week.days.getFirst().column;
            int lastColumn = week.days.getLast().column;
            unreadable += week.valuesUnder(week.otherColumns.stream()
                    .filter(column -> column > firstColumn && column < lastColumn).toList());
            for (Day day : week.days) {
                LocalDate when = date(day.year, day);
                if (filled(week.weightRow, day.column)) {
                    yearWasNeeded |= !week.hasYears;
                    Optional<BigDecimal> weight = TableReader.weight(week.weightRow.get(day.column).strip());
                    if (weight.isPresent()) {
                        weights.remove(when);
                        weights.put(when, weight.get());
                    } else {
                        unreadable++;
                    }
                }
                if (filled(week.stepsRow, day.column)) {
                    yearWasNeeded |= !week.hasYears;
                    Optional<Integer> count = TableReader.steps(week.stepsRow.get(day.column).strip());
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
        return new Attempt(new DayGridRows(table, yearWasNeeded ? assumedYear : null), daysInWellFormedWeeks);
    }

    private static boolean newestFirst(List<Week> weeks) {
        int earlier = 0;
        int later = 0;
        for (int i = 1; i < weeks.size(); i++) {
            int step = Math.floorMod(
                    dayOfYear(weeks.get(i).days.getFirst()) - dayOfYear(weeks.get(i - 1).days.getFirst()), DAYS_IN_LEAP_YEAR);
            if (step > 0 && step <= DAYS_IN_LEAP_YEAR / 2) {
                later++;
            } else {
                earlier++;
            }
        }
        return earlier > later;
    }

    private static int yearsOnWithMostDays(List<Week> placed) {
        Map<Integer, Integer> days = new LinkedHashMap<>();
        for (Week week : placed) {
            for (Day day : week.days) {
                days.merge(day.yearsOn, 1, Integer::sum);
            }
        }
        int best = 0;
        int most = -1;
        for (Map.Entry<Integer, Integer> entry : days.entrySet()) {
            if (entry.getValue() > most || (entry.getValue() == most && entry.getKey() < best)) {
                best = entry.getKey();
                most = entry.getValue();
            }
        }
        return best;
    }

    private static Day lastDayWithAValue(List<Week> placed) {
        Day last = null;
        for (Week week : placed) {
            for (Day day : week.days) {
                boolean weight = filled(week.weightRow, day.column)
                        && TableReader.weight(week.weightRow.get(day.column).strip()).isPresent();
                boolean steps = filled(week.stepsRow, day.column)
                        && TableReader.steps(week.stepsRow.get(day.column).strip()).filter(count -> count > 0).isPresent();
                if ((weight || steps) && (last == null || isLater(day, last))) {
                    last = day;
                }
            }
        }
        return last;
    }

    private static boolean isLater(Day day, Day other) {
        return day.yearsOn > other.yearsOn || (day.yearsOn == other.yearsOn && isLaterInTheYear(day, other));
    }

    private static boolean isLaterInTheYear(Day day, Day other) {
        return day.month > other.month || (day.month == other.month && day.day > other.day);
    }

    private static int dayOfYear(Day day) {
        return LocalDate.of(LEAP_YEAR, day.month, day.day).getDayOfYear();
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

    private static LocalDate date(Integer year, Day day) {
        if (year == null) {
            return null;
        }
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

    private static final class Week {
        private final List<Day> days = new ArrayList<>();
        private final List<Integer> dateShapedColumns = new ArrayList<>();
        private final List<Integer> otherColumns = new ArrayList<>();
        private final boolean wellFormed;
        private final boolean hasYears;
        private List<String> weightRow;
        private List<String> stepsRow;
        private boolean placed;

        private Week(List<String> dateRow, int labelColumn, DateOrder order) {
            boolean broken = false;
            for (int column = 0; column < dateRow.size(); column++) {
                String cell = dateRow.get(column);
                if (column == labelColumn || cell.isBlank()) {
                    continue;
                }
                Optional<LocalDate> full = DateReader.read(cell, order);
                Matcher dayAndMonth = DAY_AND_MONTH.matcher(cell);
                if (full.isPresent()) {
                    Day day = new Day(column, full.get().getDayOfMonth(), full.get().getMonthValue());
                    day.year = full.get().getYear();
                    days.add(day);
                    dateShapedColumns.add(column);
                } else if (dayAndMonth.matches()) {
                    int first = Integer.parseInt(dayAndMonth.group(1));
                    int second = Integer.parseInt(dayAndMonth.group(2));
                    Day day = order == DateOrder.DMY ? new Day(column, first, second) : new Day(column, second, first);
                    dateShapedColumns.add(column);
                    if (date(LEAP_YEAR, day) == null) {
                        broken = true;
                    } else {
                        days.add(day);
                    }
                } else if (DateReader.looksLikeDate(cell)) {
                    dateShapedColumns.add(column);
                    broken = true;
                } else {
                    otherColumns.add(column);
                }
            }
            boolean allWithYear = !days.isEmpty() && days.stream().allMatch(day -> day.year != null);
            boolean noneWithYear = days.stream().allMatch(day -> day.year == null);
            this.hasYears = allWithYear;
            if (broken || days.isEmpty()) {
                this.wellFormed = false;
            } else if (allWithYear) {
                this.wellFormed = followEachOther(days, day -> date(day.year, day));
            } else {
                this.wellFormed = noneWithYear
                        && (followEachOther(days, inYear(COMMON_YEAR)) || followEachOther(days, inYear(LEAP_YEAR)));
            }
        }

        private Function<Day, LocalDate> inYear(int year) {
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

        private int valuesUnder(List<Integer> columns) {
            int values = 0;
            for (int column : columns) {
                values += (filled(weightRow, column) ? 1 : 0) + (filled(stepsRow, column) ? 1 : 0);
            }
            return values;
        }
    }

    private static final class Day {
        private final int column;
        private final int day;
        private final int month;
        private Integer year;
        private int yearsOn;

        private Day(int column, int day, int month) {
            this.column = column;
            this.day = day;
            this.month = month;
        }
    }
}
