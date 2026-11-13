package dev.eugenlinde.activitytracker.common;

import dev.eugenlinde.activitytracker.common.exception.InvalidEntryDateException;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EntryDatePolicyTest {
    private final EntryDatePolicy policy =
            new EntryDatePolicy(Clock.fixed(Instant.parse("2026-11-13T23:30:00Z"), ZoneOffset.UTC));

    @Test
    void today_isTheUtcCalendarDay() {
        assertEquals(LocalDate.parse("2026-11-13"), policy.today());
    }

    @Test
    void latestAllowedDate_isTomorrowInUtc() {
        assertEquals(LocalDate.parse("2026-11-14"), policy.latestAllowedDate());
    }

    @Test
    void requireNotInFuture_acceptsPastTodayAndTomorrow() {
        assertDoesNotThrow(() -> policy.requireNotInFuture(LocalDate.parse("2020-01-01")));
        assertDoesNotThrow(() -> policy.requireNotInFuture(LocalDate.parse("2026-11-13")));
        assertDoesNotThrow(() -> policy.requireNotInFuture(LocalDate.parse("2026-11-14")));
    }

    @Test
    void requireNotInFuture_rejectsTheDayAfterTomorrow() {
        assertThrows(
                InvalidEntryDateException.class,
                () -> policy.requireNotInFuture(LocalDate.parse("2026-11-15")));
    }
}
