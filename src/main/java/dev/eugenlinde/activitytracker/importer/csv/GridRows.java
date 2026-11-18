package dev.eugenlinde.activitytracker.importer.csv;

import dev.eugenlinde.activitytracker.importer.WorkoutDraft;

import java.util.List;

public record GridRows(List<WorkoutDraft> drafts, List<SkippedEntry> skipped) {
}
