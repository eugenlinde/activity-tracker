package dev.eugenlinde.activitytracker.importer.dto;

import dev.eugenlinde.activitytracker.activity.model.Session;

public record WorkoutPreview(String label, Session details, boolean alreadyImported) {
}
