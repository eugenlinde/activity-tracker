package dev.eugenlinde.activitytracker.importer.csv;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public final class CsvText {

    private static final Charset SPREADSHEET_FALLBACK = Charset.forName("windows-1257");
    private static final char[] DELIMITERS = {'\t', ';', ','};
    private static final char DEFAULT_DELIMITER = ',';
    private static final int DELIMITER_SAMPLE_LINES = 20;

    private CsvText() {
    }

    public static List<List<String>> read(byte[] bytes) {
        return parse(decode(bytes));
    }

    public static String decode(byte[] bytes) {
        String text;
        try {
            text = StandardCharsets.UTF_8
                    .newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes))
                    .toString();
        } catch (CharacterCodingException notUtf8) {
            text = new String(bytes, SPREADSHEET_FALLBACK);
        }
        return text.startsWith("﻿") ? text.substring(1) : text;
    }

    public static List<List<String>> parse(String text) {
        return parse(text, guessDelimiter(text));
    }

    private static List<List<String>> parse(String text, char delimiter) {
        List<List<String>> rows = new ArrayList<>();
        List<String> row = new ArrayList<>();
        StringBuilder cell = new StringBuilder();
        boolean quoted = false;
        boolean rowHasContent = false;
        boolean cellStart = true;

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (quoted) {
                if (c == '"' && i + 1 < text.length() && text.charAt(i + 1) == '"') {
                    cell.append('"');
                    i++;
                } else if (c == '"') {
                    quoted = false;
                } else {
                    cell.append(c);
                }
                continue;
            }
            if (c == '"' && cellStart) {
                quoted = true;
                rowHasContent = true;
                cellStart = false;
            } else if (c == delimiter) {
                row.add(cell.toString());
                cell.setLength(0);
                rowHasContent = true;
                cellStart = true;
            } else if (c == '\n' || c == '\r') {
                if (c == '\r' && i + 1 < text.length() && text.charAt(i + 1) == '\n') {
                    i++;
                }
                if (rowHasContent || !cell.isEmpty()) {
                    row.add(cell.toString());
                    rows.add(row);
                }
                row = new ArrayList<>();
                cell.setLength(0);
                rowHasContent = false;
                cellStart = true;
            } else {
                cell.append(c);
                cellStart = false;
            }
        }
        if (rowHasContent || !cell.isEmpty()) {
            row.add(cell.toString());
            rows.add(row);
        }
        return rows;
    }

    private static char guessDelimiter(String text) {
        String sample = text.lines().limit(DELIMITER_SAMPLE_LINES).collect(Collectors.joining("\n"));
        char best = DEFAULT_DELIMITER;
        int bestScore = 0;
        for (char delimiter : DELIMITERS) {
            int score = consistentRows(parse(sample, delimiter));
            if (score > bestScore) {
                best = delimiter;
                bestScore = score;
            }
        }
        return best;
    }

    private static int consistentRows(List<List<String>> rows) {
        Map<Integer, Integer> rowsPerWidth = new HashMap<>();
        for (List<String> row : rows) {
            rowsPerWidth.merge(row.size(), 1, Integer::sum);
        }
        int score = 0;
        for (Map.Entry<Integer, Integer> entry : rowsPerWidth.entrySet()) {
            if (entry.getKey() >= 2 && entry.getValue() > score) {
                score = entry.getValue();
            }
        }
        return score;
    }
}
