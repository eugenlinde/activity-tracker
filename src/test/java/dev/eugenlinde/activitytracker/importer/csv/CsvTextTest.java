package dev.eugenlinde.activitytracker.importer.csv;

import org.junit.jupiter.api.Test;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CsvTextTest {

    @Test
    void read_commaSeparated_splitsRowsAndCells() {
        byte[] bytes = "Date,Weight\n2026-11-01,81.3\n".getBytes(StandardCharsets.UTF_8);

        assertEquals(List.of(List.of("Date", "Weight"), List.of("2026-11-01", "81.3")), CsvText.read(bytes));
    }

    @Test
    void read_semicolonSeparatedWithDecimalCommas_keepsTheCommasInsideCells() {
        byte[] bytes = "Kuupäev;Kaal\r\n01.11.2026;81,3\r\n02.11.2026;81,1\r\n".getBytes(StandardCharsets.UTF_8);

        assertEquals(
                List.of(List.of("Kuupäev", "Kaal"), List.of("01.11.2026", "81,3"), List.of("02.11.2026", "81,1")),
                CsvText.read(bytes)
        );
    }

    @Test
    void read_tabSeparated_isRecognised() {
        byte[] bytes = "Date\tSteps\n2026-11-01\t10234\n".getBytes(StandardCharsets.UTF_8);

        assertEquals(List.of(List.of("Date", "Steps"), List.of("2026-11-01", "10234")), CsvText.read(bytes));
    }

    @Test
    void read_quotedCellWithDelimiterQuoteAndNewline_staysOneCell() {
        byte[] bytes = "name,comment\n\"Kükk, kangiga\",\"ütles \"\"hea\"\"\nteine rida\"\n".getBytes(StandardCharsets.UTF_8);

        assertEquals(
                List.of(List.of("name", "comment"), List.of("Kükk, kangiga", "ütles \"hea\"\nteine rida")),
                CsvText.read(bytes)
        );
    }

    @Test
    void read_utf8ByteOrderMark_isNotPartOfTheFirstCell() {
        byte[] text = "Date,Weight\n".getBytes(StandardCharsets.UTF_8);
        byte[] bytes = new byte[text.length + 3];
        bytes[0] = (byte) 0xEF;
        bytes[1] = (byte) 0xBB;
        bytes[2] = (byte) 0xBF;
        System.arraycopy(text, 0, bytes, 3, text.length);

        assertEquals(List.of(List.of("Date", "Weight")), CsvText.read(bytes));
    }

    @Test
    void decode_windows1257FromEstonianExcel_keepsEstonianLetters() {
        byte[] bytes = "Kükk;Rinnalt surumine õäöüšž\n".getBytes(Charset.forName("windows-1257"));

        assertEquals("Kükk;Rinnalt surumine õäöüšž\n", CsvText.decode(bytes));
    }

    @Test
    void read_lastLineWithoutNewline_isStillARow() {
        byte[] bytes = "a,b\nc,d".getBytes(StandardCharsets.UTF_8);

        assertEquals(List.of(List.of("a", "b"), List.of("c", "d")), CsvText.read(bytes));
    }

    @Test
    void read_emptyInput_hasNoRows() {
        assertEquals(List.of(), CsvText.read(new byte[0]));
    }

    @Test
    void read_semicolonFileWithCommaInTheHeaderAndDecimalCommas_splitsOnSemicolons() {
        byte[] bytes = "Kuupäev;Kaal, kg\n01.11.2026;81,3\n02.11.2026;81,1\n".getBytes(StandardCharsets.UTF_8);

        assertEquals(
                List.of(List.of("Kuupäev", "Kaal, kg"), List.of("01.11.2026", "81,3"), List.of("02.11.2026", "81,1")),
                CsvText.read(bytes)
        );
    }

    @Test
    void read_headerlessSemicolonRowsWithDecimalCommas_splitOnSemicolons() {
        byte[] bytes = "03.11.2026 07:15;81,35\n04.11.2026 07:20;80,95\n".getBytes(StandardCharsets.UTF_8);

        assertEquals(
                List.of(List.of("03.11.2026 07:15", "81,35"), List.of("04.11.2026 07:20", "80,95")),
                CsvText.read(bytes)
        );
    }

    @Test
    void read_tabFileWithCommasInCells_splitsOnTabs() {
        byte[] bytes = "Kuupäev\tKaal, kg\n01.11.2026\t81,3\n".getBytes(StandardCharsets.UTF_8);

        assertEquals(List.of(List.of("Kuupäev", "Kaal, kg"), List.of("01.11.2026", "81,3")), CsvText.read(bytes));
    }

    @Test
    void read_quoteInTheMiddleOfACell_isAnOrdinaryCharacter() {
        byte[] bytes = "01.11.2026;81,3;5\" ketas\n02.11.2026;81,1;x\n03.11.2026;80,9;y\n".getBytes(StandardCharsets.UTF_8);

        assertEquals(List.of(
                List.of("01.11.2026", "81,3", "5\" ketas"),
                List.of("02.11.2026", "81,1", "x"),
                List.of("03.11.2026", "80,9", "y")
        ), CsvText.read(bytes));
    }
}
