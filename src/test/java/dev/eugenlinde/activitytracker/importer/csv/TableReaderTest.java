package dev.eugenlinde.activitytracker.importer.csv;

import dev.eugenlinde.activitytracker.importer.llm.TableMapping;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TableReaderTest {

    private static LocalDate date(String iso) {
        return LocalDate.parse(iso);
    }

    @Test
    void read_weightAndStepsColumns_readsBothKinds() {
        List<List<String>> rows = List.of(
                List.of("Kuupäev", "Kaal", "Sammud"),
                List.of("01.11.2026", "81,3", "10 234"),
                List.of("02.11.2026", "81,1 kg", "9876")
        );

        TableRows result = TableReader.read(rows, new TableMapping(1, 0, DateOrder.DMY, 1, 2));

        assertEquals(List.of(
                new WeightRow(date("2026-11-01"), new BigDecimal("81.3")),
                new WeightRow(date("2026-11-02"), new BigDecimal("81.1"))
        ), result.weights());
        assertEquals(List.of(
                new StepsRow(date("2026-11-01"), 10234),
                new StepsRow(date("2026-11-02"), 9876)
        ), result.steps());
        assertEquals(0, result.unreadable());
    }

    @Test
    void read_stepsWithThousandsSeparatorsOrSpreadsheetDecimals_areWholeNumbers() {
        List<List<String>> rows = List.of(
                List.of("2026-11-01", "10,234"),
                List.of("2026-11-02", "10.234"),
                List.of("2026-11-03", "10 234"),
                List.of("2026-11-04", "10234.0"),
                List.of("2026-11-05", "987")
        );

        TableRows result = TableReader.read(rows, new TableMapping(0, 0, DateOrder.YMD, null, 1));

        assertEquals(List.of(10234, 10234, 10234, 10234, 987), result.steps().stream().map(StepsRow::steps).toList());
        assertEquals(0, result.unreadable());
    }

    @Test
    void read_weightIsRoundedToOneDecimal() {
        List<List<String>> rows = List.of(List.of("2026-11-01", "81.25"), List.of("2026-11-02", "80"));

        TableRows result = TableReader.read(rows, new TableMapping(0, 0, DateOrder.YMD, 1, null));

        assertEquals(
                List.of(new BigDecimal("81.3"), new BigDecimal("80.0")),
                result.weights().stream().map(WeightRow::weightKg).toList()
        );
    }

    @Test
    void read_blankRowsAndEmptyValueCells_areSkippedWithoutCountingAsUnreadable() {
        List<List<String>> rows = List.of(
                List.of("2026-11-01", "81.3", ""),
                List.of("", "", ""),
                List.of("2026-11-02", "", "5000")
        );

        TableRows result = TableReader.read(rows, new TableMapping(0, 0, DateOrder.YMD, 1, 2));

        assertEquals(1, result.weights().size());
        assertEquals(1, result.steps().size());
        assertEquals(0, result.unreadable());
    }

    @Test
    void read_badDateOrOutOfRangeValue_isCountedAsUnreadable() {
        List<List<String>> rows = List.of(
                List.of("kokku", "81.3", "5000"),
                List.of("2026-11-02", "810", "5000"),
                List.of("2026-11-03", "81.3", "250000"),
                List.of("2026-11-04", "abc", "tuhat")
        );

        TableRows result = TableReader.read(rows, new TableMapping(0, 0, DateOrder.YMD, 1, 2));

        assertEquals(List.of(new WeightRow(date("2026-11-03"), new BigDecimal("81.3"))), result.weights());
        assertEquals(List.of(new StepsRow(date("2026-11-02"), 5000)), result.steps());
        assertEquals(5, result.unreadable());
    }

    @Test
    void read_sameDateTwice_keepsTheLastValue() {
        List<List<String>> rows = List.of(List.of("2026-11-01", "81.3"), List.of("2026-11-01", "80.9"));

        TableRows result = TableReader.read(rows, new TableMapping(0, 0, DateOrder.YMD, 1, null));

        assertEquals(List.of(new WeightRow(date("2026-11-01"), new BigDecimal("80.9"))), result.weights());
    }

    @Test
    void read_rowShorterThanTheMappedColumns_isNotAnError() {
        List<List<String>> rows = List.of(List.of("2026-11-01"), List.of("2026-11-02", "81.3"));

        TableRows result = TableReader.read(rows, new TableMapping(0, 0, DateOrder.YMD, 1, 2));

        assertEquals(1, result.weights().size());
        assertEquals(0, result.steps().size());
        assertEquals(0, result.unreadable());
    }
}
