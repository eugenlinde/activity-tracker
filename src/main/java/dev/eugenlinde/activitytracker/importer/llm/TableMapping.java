package dev.eugenlinde.activitytracker.importer.llm;

import dev.eugenlinde.activitytracker.importer.csv.DateOrder;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record TableMapping(
        @Min(0) @Max(50) int headerRows,
        @Min(0) @Max(200) int dateColumn,
        @NotNull DateOrder dateOrder,
        @Min(0) @Max(200) Integer weightColumn,
        @Min(0) @Max(200) Integer stepsColumn
) {
}
