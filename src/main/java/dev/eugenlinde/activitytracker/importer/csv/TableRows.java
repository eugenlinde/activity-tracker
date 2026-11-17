package dev.eugenlinde.activitytracker.importer.csv;

import java.util.List;

public record TableRows(List<WeightRow> weights, List<StepsRow> steps, int unreadable) {
}
