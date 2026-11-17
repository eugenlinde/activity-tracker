package dev.eugenlinde.activitytracker.importer.llm;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public record FileAnalysis(
        @NotNull Kind kind,
        @Valid TableMapping table
) {
    public enum Kind {
        WORKOUT_GRID,
        TABLE,
        UNKNOWN
    }
}
