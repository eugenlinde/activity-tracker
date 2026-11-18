package dev.eugenlinde.activitytracker.importer.csv;

import dev.eugenlinde.activitytracker.importer.llm.DayGridMapping;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DayGridReaderTest {

    private static final String SHEET = """
            Harjumused,,,,,,,,,
            <<<JAANUAR,,,,,,,,,
            Kuupäev,5.01,6.01,7.01,8.01,9.01,10.01,11.01,Keskmine,
            Kaal,81.3,"81,1",,80.9,,,,81.1,eesmärk 80kg
            Toitumine,ok,ok,,,,,,,
            Sammud,10234,9876,,,12 001,,,10000.5,
            Kuupäev,12.01,13.01,14.01,15.01,16.01,17.01,18.01,Keskmine,
            Kaal,80.7,,,,,,,80.7,
            Sammud,,8120,,,,,,8120.0,
            """;
    private static final DayGridMapping MAPPING = new DayGridMapping(0, "Kuupäev", "Kaal", "Sammud", DateOrder.DMY);
    private static final LocalDate TODAY = LocalDate.parse("2026-11-18");

    private static DayGridRows read(String sheet, DayGridMapping mapping, Integer year, LocalDate today) {
        return DayGridReader.read(CsvText.parse(sheet), mapping, year, today);
    }

    private static WeightRow weight(String date, String kg) {
        return new WeightRow(LocalDate.parse(date), new BigDecimal(kg));
    }

    private static StepsRow steps(String date, int count) {
        return new StepsRow(LocalDate.parse(date), count);
    }

    @Test
    void read_takesEachValueFromTheDayColumnOfItsOwnWeek() {
        DayGridRows grid = read(SHEET, MAPPING, 2026, TODAY);

        assertEquals(List.of(
                weight("2026-01-05", "81.3"), weight("2026-01-06", "81.1"), weight("2026-01-08", "80.9"),
                weight("2026-01-12", "80.7")
        ), grid.table().weights());
        assertEquals(List.of(
                steps("2026-01-05", 10234), steps("2026-01-06", 9876), steps("2026-01-09", 12001),
                steps("2026-01-13", 8120)
        ), grid.table().steps());
    }

    @Test
    void read_columnWithoutADate_isNeverRead_soTheAverageIsNotAnEntryAndNotAnError() {
        DayGridRows grid = read(SHEET, MAPPING, 2026, TODAY);

        assertEquals(0, grid.table().unreadable());
        assertEquals(4, grid.table().weights().size());
    }

    @Test
    void read_datesWithoutAYear_reportTheYearThatWasUsed() {
        assertEquals(2026, read(SHEET, MAPPING, 2026, TODAY).assumedYear());
        assertEquals(2024, read(SHEET, MAPPING, 2024, TODAY).assumedYear());
        assertEquals(LocalDate.parse("2024-01-05"), read(SHEET, MAPPING, 2024, TODAY).table().weights().getFirst().date());
    }

    @Test
    void read_noYearGiven_usesTheLatestYearInWhichTheLastFilledDayIsNotInTheFuture() {
        assertEquals(2026, read(SHEET, MAPPING, null, LocalDate.parse("2026-11-18")).assumedYear());
        assertEquals(2026, read(SHEET, MAPPING, null, LocalDate.parse("2026-01-13")).assumedYear());
        assertEquals(2025, read(SHEET, MAPPING, null, LocalDate.parse("2026-01-12")).assumedYear());
        assertEquals(2027, read(SHEET, MAPPING, null, LocalDate.parse("2027-03-01")).assumedYear());
    }

    @Test
    void read_emptyDaysAfterTheLastFilledOne_doNotPushTheYearBack() {
        String sheet = SHEET + "Kuupäev,21.12,22.12,23.12,24.12,25.12,26.12,27.12,Keskmine,\nKaal,,,,,,,,,\n";

        assertEquals(2026, read(sheet, MAPPING, null, LocalDate.parse("2026-11-18")).assumedYear());
    }

    @Test
    void read_weekAcrossNewYear_continuesIntoTheNextYear() {
        String sheet = """
                Kuupäev,28.12,29.12,30.12,31.12,1.01,2.01,3.01
                Kaal,80.1,,,80.4,80.5,,
                Kuupäev,4.01,5.01,6.01,7.01,8.01,9.01,10.01
                Kaal,80.6,,,,,,
                """;

        DayGridRows given = read(sheet, MAPPING, 2026, LocalDate.parse("2027-06-01"));
        DayGridRows guessed = read(sheet, MAPPING, null, LocalDate.parse("2027-01-05"));

        assertEquals(List.of(weight("2026-12-28", "80.1"), weight("2026-12-31", "80.4"), weight("2027-01-01", "80.5"),
                weight("2027-01-04", "80.6")), given.table().weights());
        assertEquals(2026, guessed.assumedYear());
        assertEquals(given.table().weights(), guessed.table().weights());
    }

    @Test
    void read_datesWithAYear_needNoYearAndIgnoreTheOneGiven() {
        String sheet = """
                Kuupäev,05.01.2025,06.01.2025,07.01.2025
                Kaal,81.3,,81.0
                """;

        DayGridRows grid = read(sheet, MAPPING, 2026, TODAY);

        assertNull(grid.assumedYear());
        assertEquals(List.of(weight("2025-01-05", "81.3"), weight("2025-01-07", "81.0")), grid.table().weights());
    }

    @Test
    void read_dateRowWhoseDaysDoNotFollowEachOther_isNotReadAndItsValuesAreCounted() {
        String sheet = """
                Kuupäev,9.10,10.1,11.10,12.10
                Kaal,81.3,81.2,,81.0
                Sammud,5000,,,
                Kuupäev,13.10,14.10,15.10,16.10
                Kaal,80.9,,,
                """;

        DayGridRows grid = read(sheet, MAPPING, 2026, TODAY);

        assertEquals(List.of(weight("2026-10-13", "80.9")), grid.table().weights());
        assertTrue(grid.table().steps().isEmpty());
        assertEquals(4, grid.table().unreadable());
    }

    @Test
    void read_monthBeforeDay_isWorkedOutFromTheDaysFollowingEachOther_notTakenFromTheModel() {
        String sheet = """
                Date,01/05,01/06,01/07,01/08
                Weight,81.3,81.2,,81.0
                """;
        DayGridMapping saysDayFirst = new DayGridMapping(0, "Date", "Weight", null, DateOrder.DMY);

        DayGridRows grid = read(sheet, saysDayFirst, 2026, TODAY);

        assertEquals(List.of(weight("2026-01-05", "81.3"), weight("2026-01-06", "81.2"), weight("2026-01-08", "81.0")),
                grid.table().weights());
    }

    @Test
    void read_dayThatDoesNotExistInThatYear_makesTheWeekUnreadable() {
        String sheet = """
                Kuupäev,27.02,28.02,29.02,1.03
                Kaal,81.3,,,81.0
                """;

        assertEquals(2, read(sheet, MAPPING, 2026, TODAY).table().unreadable());
        assertEquals(2, read(sheet, MAPPING, 2028, LocalDate.parse("2028-06-01")).table().weights().size());
    }

    @Test
    void read_valueThatIsNotANumberOrOutOfRange_isCounted() {
        String sheet = """
                Kuupäev,5.01,6.01,7.01,8.01
                Kaal,haige,7.5,81.3,
                Sammud,palju,250000,,-5
                """;

        DayGridRows grid = read(sheet, MAPPING, 2026, TODAY);

        assertEquals(List.of(weight("2026-01-07", "81.3")), grid.table().weights());
        assertTrue(grid.table().steps().isEmpty());
        assertEquals(5, grid.table().unreadable());
    }

    @Test
    void read_sameDayTwice_lastOneWins() {
        String sheet = """
                Kuupäev,5.01,6.01
                Kaal,81.3,81.2
                Kuupäev,6.01,7.01
                Kaal,80.0,
                """;

        assertEquals(List.of(weight("2026-01-05", "81.3"), weight("2026-01-06", "80.0")),
                read(sheet, MAPPING, 2026, TODAY).table().weights());
    }

    @Test
    void read_labelsAreMatchedWithoutRegardToSpacing_butNotLoosely() {
        String sheet = "  Kuupäev ,5.01,6.01\nSammude  arv,5000,6000\nSammude arv kokku,1,2\n";
        DayGridMapping mapping = new DayGridMapping(0, "Kuupäev", null, "Sammude arv", DateOrder.DMY);

        DayGridRows grid = read(sheet, mapping, 2026, TODAY);

        assertEquals(List.of(steps("2026-01-05", 5000), steps("2026-01-06", 6000)), grid.table().steps());
        assertTrue(grid.table().weights().isEmpty());
    }

    @Test
    void read_labelsThatAreNotInTheSheet_readNothing() {
        DayGridRows grid = read(SHEET, new DayGridMapping(0, "Date", "Weight", "Steps", DateOrder.DMY), 2026, TODAY);

        assertTrue(grid.table().weights().isEmpty() && grid.table().steps().isEmpty());
        assertEquals(0, grid.table().unreadable());
        assertNull(grid.assumedYear());
    }

    @Test
    void read_labelColumnOutsideTheSheet_readsNothing() {
        DayGridRows grid = read(SHEET, new DayGridMapping(40, "Kuupäev", "Kaal", "Sammud", DateOrder.DMY), 2026, TODAY);

        assertTrue(grid.table().weights().isEmpty());
    }

    @Test
    void read_weightKeepsOneDecimal_likeAManualEntry() {
        String sheet = "Kuupäev,5.01,6.01\nKaal,74.45,75.25\n";

        assertEquals(List.of(weight("2026-01-05", "74.5"), weight("2026-01-06", "75.3")),
                read(sheet, MAPPING, 2026, TODAY).table().weights());
    }
}
