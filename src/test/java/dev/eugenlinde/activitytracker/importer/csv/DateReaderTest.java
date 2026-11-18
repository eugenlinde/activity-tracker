package dev.eugenlinde.activitytracker.importer.csv;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DateReaderTest {

    @ParameterizedTest
    @CsvSource({
            "3.11.2026, DMY, 2026-11-03",
            "03.11.2026, DMY, 2026-11-03",
            "03/11/26, DMY, 2026-11-03",
            "3-11-2026, DMY, 2026-11-03",
            "11/3/2026, MDY, 2026-11-03",
            "2026-11-03, YMD, 2026-11-03",
            "2026/11/3, YMD, 2026-11-03",
            "2026-11-03 00:00:00, YMD, 2026-11-03",
            "' 3.11.2026 ', DMY, 2026-11-03"
    })
    void read_recognisedForms(String cell, DateOrder order, LocalDate expected) {
        assertEquals(Optional.of(expected), DateReader.read(cell, order));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "  ", "Kuupäev", "31.02.2026", "3 Nov 2026", "13/13/2026", "2026-11"})
    void read_notADate_isEmpty(String cell) {
        assertEquals(Optional.empty(), DateReader.read(cell, DateOrder.DMY));
    }

    @Test
    void read_null_isEmpty() {
        assertEquals(Optional.empty(), DateReader.read(null, DateOrder.DMY));
    }

    @Test
    void read_sameCellDifferentOrder_givesDifferentDates() {
        assertEquals(Optional.of(LocalDate.parse("2026-11-03")), DateReader.read("03/11/2026", DateOrder.DMY));
        assertEquals(Optional.of(LocalDate.parse("2026-03-11")), DateReader.read("03/11/2026", DateOrder.MDY));
    }

    @ParameterizedTest
    @ValueSource(strings = {"03.11.206", "1.1.1850", "03.11.2206"})
    void read_implausibleYear_isEmpty(String cell) {
        assertEquals(Optional.empty(), DateReader.read(cell, DateOrder.DMY));
    }
}
