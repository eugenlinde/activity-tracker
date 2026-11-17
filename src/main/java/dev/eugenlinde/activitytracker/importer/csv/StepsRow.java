package dev.eugenlinde.activitytracker.importer.csv;

import java.time.LocalDate;

public record StepsRow(LocalDate date, int steps) {
}
