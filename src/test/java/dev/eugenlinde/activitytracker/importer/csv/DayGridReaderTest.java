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
        return DayGridReader.read(CsvText.parse(sheet), mapping, year, null, today);
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
    void read_weekAcrossNewYear_continuesIntoTheNextYear_andTheYearNamesWhereMostDaysAre() {
        String sheet = """
                Kuupäev,28.12,29.12,30.12,31.12,1.01,2.01,3.01
                Kaal,80.1,,,80.4,80.5,,
                Kuupäev,4.01,5.01,6.01,7.01,8.01,9.01,10.01
                Kaal,80.6,,,,,,
                """;

        DayGridRows given = read(sheet, MAPPING, 2027, LocalDate.parse("2027-06-01"));
        DayGridRows guessed = read(sheet, MAPPING, null, LocalDate.parse("2027-01-05"));

        assertEquals(List.of(weight("2026-12-28", "80.1"), weight("2026-12-31", "80.4"), weight("2027-01-01", "80.5"),
                weight("2027-01-04", "80.6")), given.table().weights());
        assertEquals(2027, guessed.assumedYear());
        assertEquals(given.table().weights(), guessed.table().weights());
    }

    @Test
    void read_sheetThatStartsInTheLastDaysOfDecember_putsTheGivenYearOnTheMonthsThatFollow() {
        String sheet = """
                Kuupäev,30.12,31.12,1.01,2.01,3.01,4.01,5.01,Keskmine
                Kaal,80.0,,81.3,81.2,,,81.0,81.2
                Kuupäev,6.01,7.01,8.01,9.01,10.01,11.01,12.01,Keskmine
                Kaal,80.9,,,,,,80.5,80.7
                """;

        DayGridRows given = read(sheet, MAPPING, 2025, LocalDate.parse("2026-11-17"));
        DayGridRows guessed = read(sheet, MAPPING, null, LocalDate.parse("2026-11-17"));

        assertEquals(2025, given.assumedYear());
        assertEquals(List.of(weight("2024-12-30", "80.0"), weight("2025-01-01", "81.3"), weight("2025-01-02", "81.2"),
                weight("2025-01-05", "81.0"), weight("2025-01-06", "80.9"), weight("2025-01-12", "80.5")),
                given.table().weights());
        assertEquals(2026, guessed.assumedYear());
        assertEquals(LocalDate.parse("2026-01-12"), guessed.table().weights().getLast().date());
    }

    @Test
    void read_newYearBetweenTwoWeeks_isFollowed() {
        String sheet = """
                Kuupäev,25.12,26.12,27.12,28.12,29.12,30.12,31.12
                Kaal,80.0,,,,,,80.2
                Kuupäev,1.01,2.01,3.01,4.01,5.01,6.01,7.01
                Kaal,80.3,,,,,,
                Kuupäev,8.01,9.01,10.01,11.01,12.01,13.01,14.01
                Kaal,80.4,,,,,,
                """;

        assertEquals(List.of(weight("2025-12-25", "80.0"), weight("2025-12-31", "80.2"), weight("2026-01-01", "80.3"),
                weight("2026-01-08", "80.4")), read(sheet, MAPPING, 2026, TODAY).table().weights());
    }

    @Test
    void read_weeksListedNewestFirst_areReadInTheirRealOrder() {
        String sheet = """
                Kuupäev,12.01,13.01,14.01
                Kaal,80.4,,
                Kuupäev,5.01,6.01,7.01
                Kaal,80.3,,
                Kuupäev,29.12,30.12,31.12
                Kaal,80.2,,
                Kuupäev,22.12,23.12,24.12
                Kaal,80.1,,
                """;

        DayGridRows given = read(sheet, MAPPING, 2025, LocalDate.parse("2026-11-17"));
        DayGridRows guessed = read(sheet, MAPPING, null, LocalDate.parse("2026-01-20"));

        assertEquals(List.of(weight("2026-01-12", "80.4"), weight("2026-01-05", "80.3"), weight("2025-12-29", "80.2"),
                weight("2025-12-22", "80.1")), given.table().weights());
        assertEquals(given.table().weights(), guessed.table().weights());
        assertEquals(0, given.table().unreadable());
    }

    @Test
    void read_weekThatRepeatsOrFallsFarFromTheOneBefore_isNotReadAndItsValuesAreCounted() {
        String sheet = """
                Kuupäev,5.01,6.01,7.01
                Kaal,81.3,81.2,
                Kuupäev,6.01,7.01,8.01
                Kaal,80.0,,
                Kuupäev,8.01,9.01,10.01
                Kaal,81.0,,
                Kuupäev,15.06,16.06,17.06
                Kaal,79.0,79.1,
                """;

        DayGridRows grid = read(sheet, MAPPING, 2026, TODAY);

        assertEquals(List.of(weight("2026-01-05", "81.3"), weight("2026-01-06", "81.2"), weight("2026-01-08", "81.0")),
                grid.table().weights());
        assertEquals(3, grid.table().unreadable());
    }

    @Test
    void read_wholeWeeksOfOctoberExportedAsNumbers_areNotMistakenForJanuary() {
        String sheet = """
                Kuupäev,21.09,22.09,23.09,24.09,25.09,26.09,27.09
                Kaal,80.0,,,,,,
                Kuupäev,28.09,29.09,30.09,1.1,2.1,3.1,4.1
                Kaal,80.1,,,,,,80.2
                Kuupäev,5.1,6.1,7.1,8.1,9.1,10.1,11.1
                Kaal,80.3,,,,,80.4,
                Kuupäev,26.1,27.1,28.1,29.1,30.1,31.1,1.11
                Kaal,80.5,,,,,,
                Kuupäev,2.11,3.11,4.11,5.11,6.11,7.11,8.11
                Kaal,80.6,,,,,,
                """;

        DayGridRows grid = read(sheet, MAPPING, 2025, LocalDate.parse("2026-06-01"));

        assertEquals(List.of(weight("2025-09-21", "80.0"), weight("2025-11-02", "80.6")), grid.table().weights());
        assertEquals(5, grid.table().unreadable());
    }

    @Test
    void read_weekWithGapsBetweenItsDays_isNotHalfReadInAnotherDateOrder() {
        String everyOtherDay = "Kuupäev,12.01,14.01,16.01\nKaal,80.0,81.0,82.0\n";
        String oneTypo = "Kuupäev,12.01,13.01,15.01,16.01\nKaal,80.0,81.0,82.0,83.0\n";
        String weekly = """
                Kuupäev,5.01,12.01,19.01,26.01
                Kaal,80.0,81.0,82.0,83.0
                Kuupäev,7.09,14.09,21.09,28.09
                Kaal,84.0,85.0,86.0,87.0
                """;

        for (String sheet : List.of(everyOtherDay, oneTypo, weekly)) {
            DayGridRows grid = read(sheet, MAPPING, 2026, TODAY);
            assertTrue(grid.table().weights().isEmpty(), sheet);
            assertEquals(sheet.equals(everyOtherDay) ? 3 : sheet.equals(oneTypo) ? 4 : 8, grid.table().unreadable(), sheet);
        }
    }

    @Test
    void read_dayOfTheMonthThatDoesNotExist_makesTheWeekUnreadable() {
        DayGridRows grid = read("Kuupäev,30.01,31.01,32.01,2.02\nKaal,80.0,81.0,82.0,83.0\n", MAPPING, 2026, TODAY);

        assertTrue(grid.table().weights().isEmpty());
        assertEquals(4, grid.table().unreadable());
    }

    @Test
    void read_valueUnderADateThatCouldNotBeRead_isCounted_butTheAverageColumnIsNot() {
        String sheet = """
                Kuupäev,5.01,6.01,"7,01",8.01,9.01,Keskmine
                Kaal,80.0,81.0,82.0,83.0,84.0,82.0
                """;

        DayGridRows grid = read(sheet, MAPPING, 2026, TODAY);

        assertEquals(4, grid.table().weights().size());
        assertEquals(1, grid.table().unreadable());
    }

    @Test
    void read_weeksInAFormatThatIsNotADate_areCounted() {
        DayGridRows grid = read("Kuupäev,E 5.01,T 6.01,K 7.01\nKaal,80.0,81.0,\n", MAPPING, 2026, TODAY);

        assertTrue(grid.table().weights().isEmpty());
        assertEquals(2, grid.table().unreadable());
    }

    @Test
    void read_noYearGiven_placeholdersInLaterWeeksDoNotPushTheYearBack() {
        String sheet = """
                Kuupäev,5.01,6.01,7.01
                Kaal,81.3,,
                Kuupäev,8.01,9.01,10.01
                Kaal,-,-,-
                Sammud,0,0,0
                """;

        DayGridRows grid = read(sheet, MAPPING, null, LocalDate.parse("2026-01-06"));

        assertEquals(2026, grid.assumedYear());
        assertEquals(List.of(weight("2026-01-05", "81.3")), grid.table().weights());
    }

    @Test
    void read_yearHint_isUsedOnlyWhenItDoesNotPutFilledDaysInTheFuture() {
        String sheet = "Kuupäev,1.12,2.12,3.12\nKaal,81.3,,\n";
        LocalDate today = LocalDate.parse("2026-11-17");

        assertEquals(2024, DayGridReader.read(CsvText.parse(sheet), MAPPING, null, 2024, today).assumedYear());
        assertEquals(2025, DayGridReader.read(CsvText.parse(sheet), MAPPING, null, 2026, today).assumedYear());
        assertEquals(2026, DayGridReader.read(CsvText.parse(sheet), MAPPING, 2026, 2024, today).assumedYear());
    }

    @Test
    void read_rowWithTheWeightLabelAfterTheWeekHasEnded_doesNotBelongToIt() {
        String sheet = """
                Kuupäev,5.01,6.01,7.01
                Sammud,5000,,
                ,,,
                Kaal,81.0,80.0,79.0
                """;

        DayGridRows grid = read(sheet, MAPPING, 2026, TODAY);

        assertTrue(grid.table().weights().isEmpty());
        assertEquals(1, grid.table().steps().size());
    }

    @Test
    void read_datesWithAYearNextToAnEmptyWeekWithout_reportNoAssumedYear() {
        String sheet = """
                Kuupäev,05.01.2025,06.01.2025,07.01.2025
                Kaal,81.3,,81.0
                Kuupäev,8.01,9.01,10.01
                Kaal,,,
                """;

        DayGridRows grid = read(sheet, MAPPING, null, TODAY);

        assertEquals(2, grid.table().weights().size());
        assertNull(grid.assumedYear());
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
    void read_labelsAreMatchedWithoutRegardToSpacingOrCase_butNotLoosely() {
        String sheet = "  KUUPÄEV ,5.01,6.01\nsammude  arv,5000,6000\nSammude arv kokku,1,2\n";
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

        assertTrue(grid.table().weights().isEmpty() && grid.table().steps().isEmpty());
        assertEquals(0, grid.table().unreadable());
        assertNull(grid.assumedYear());
    }

    @Test
    void read_weightKeepsOneDecimal_likeAManualEntry() {
        String sheet = "Kuupäev,5.01,6.01\nKaal,74.45,75.25\n";

        assertEquals(List.of(weight("2026-01-05", "74.5"), weight("2026-01-06", "75.3")),
                read(sheet, MAPPING, 2026, TODAY).table().weights());
    }
}
