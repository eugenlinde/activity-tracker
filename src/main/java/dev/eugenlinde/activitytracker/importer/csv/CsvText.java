package dev.eugenlinde.activitytracker.importer.csv;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public final class CsvText {

    private static final Charset SPREADSHEET_FALLBACK = Charset.forName("windows-1257");
    private static final char[] DELIMITERS = {',', ';', '\t'};
    private static final int DELIMITER_SAMPLE_LINES = 10;

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
        char delimiter = guessDelimiter(text);
        List<List<String>> rows = new ArrayList<>();
        List<String> row = new ArrayList<>();
        StringBuilder cell = new StringBuilder();
        boolean quoted = false;
        boolean rowHasContent = false;

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
            if (c == '"') {
                quoted = true;
                rowHasContent = true;
            } else if (c == delimiter) {
                row.add(cell.toString());
                cell.setLength(0);
                rowHasContent = true;
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
            } else {
                cell.append(c);
            }
        }
        if (rowHasContent || !cell.isEmpty()) {
            row.add(cell.toString());
            rows.add(row);
        }
        return rows;
    }

    private static char guessDelimiter(String text) {
        int[] counts = new int[DELIMITERS.length];
        boolean quoted = false;
        int lines = 0;
        for (int i = 0; i < text.length() && lines < DELIMITER_SAMPLE_LINES; i++) {
            char c = text.charAt(i);
            if (c == '"') {
                quoted = !quoted;
            } else if (!quoted && c == '\n') {
                lines++;
            } else if (!quoted) {
                for (int d = 0; d < DELIMITERS.length; d++) {
                    if (c == DELIMITERS[d]) {
                        counts[d]++;
                    }
                }
            }
        }
        int best = 0;
        for (int d = 1; d < DELIMITERS.length; d++) {
            if (counts[d] > counts[best]) {
                best = d;
            }
        }
        return DELIMITERS[best];
    }
}
