package dev.eugenlinde.activitytracker.importer.csv;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class DateReader {

    private static final Pattern THREE_NUMBERS = Pattern.compile("^\\s*(\\d{1,4})[./\\-](\\d{1,2})[./\\-](\\d{1,4})(?:[ T].*)?\\s*$");

    private DateReader() {
    }

    public static Optional<LocalDate> read(String cell, DateOrder order) {
        if (cell == null) {
            return Optional.empty();
        }
        Matcher matcher = THREE_NUMBERS.matcher(cell);
        if (!matcher.matches()) {
            return Optional.empty();
        }
        int first = Integer.parseInt(matcher.group(1));
        int second = Integer.parseInt(matcher.group(2));
        int third = Integer.parseInt(matcher.group(3));
        try {
            return Optional.of(switch (order) {
                case DMY -> LocalDate.of(fullYear(third), second, first);
                case MDY -> LocalDate.of(fullYear(third), first, second);
                case YMD -> LocalDate.of(fullYear(first), second, third);
            });
        } catch (DateTimeException notADate) {
            return Optional.empty();
        }
    }

    private static int fullYear(int year) {
        return year < 100 ? 2000 + year : year;
    }
}
