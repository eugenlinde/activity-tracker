package dev.eugenlinde.activitytracker.importer.csv;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public final class GridText {

    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    private GridText() {
    }

    public static String indexed(List<List<String>> rows) {
        List<String> lines = new ArrayList<>();
        for (int number = 1; number <= rows.size(); number++) {
            List<String> row = rows.get(number - 1);
            List<String> cells = new ArrayList<>();
            for (int column = 0; column < row.size(); column++) {
                String cell = WHITESPACE.matcher(row.get(column).strip()).replaceAll(" ");
                if (!cell.isEmpty()) {
                    cells.add("[" + column + "] " + cell);
                }
            }
            if (!cells.isEmpty()) {
                lines.add(number + ": " + String.join(" | ", cells));
            }
        }
        return String.join("\n", lines);
    }
}
