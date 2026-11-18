package dev.eugenlinde.activitytracker.importer.csv;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GridTextTest {

    @Test
    void indexed_writesEachFilledCellWithItsColumnNumberAfterTheRowNumber() {
        List<List<String>> rows = List.of(
                List.of("", "Treening 1", "", "Seeriad", "Raskus"),
                List.of("5-20", "Kükk", "", "2", "102,5")
        );

        assertEquals("""
                1: [1] Treening 1 | [3] Seeriad | [4] Raskus
                2: [0] 5-20 | [1] Kükk | [3] 2 | [4] 102,5""", GridText.indexed(rows));
    }

    @Test
    void indexed_leavesOutEmptyRowsButKeepsCountingThem() {
        List<List<String>> rows = List.of(
                List.of("a", ""),
                List.of("", "", ""),
                List.of(" ", "\t"),
                List.of("", "b")
        );

        assertEquals("1: [0] a\n4: [1] b", GridText.indexed(rows));
    }

    @Test
    void indexed_stripsCells() {
        assertEquals("1: [0] 12, 11 | [1] KP", GridText.indexed(List.of(List.of("  12, 11 ", " KP"))));
    }

    @Test
    void indexed_keepsACellWithLineBreaksOnOneLine() {
        List<List<String>> rows = List.of(List.of("esimene rida\nteine\r\n  kolmas", "x"));

        assertEquals("1: [0] esimene rida teine kolmas | [1] x", GridText.indexed(rows));
    }

    @Test
    void indexed_nothingFilled_isEmptyText() {
        assertEquals("", GridText.indexed(List.of(List.of("", ""), List.of())));
    }
}
