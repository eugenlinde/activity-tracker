package dev.eugenlinde.activitytracker.importer.llm;

import dev.eugenlinde.activitytracker.importer.csv.DateOrder;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record DayGridMapping(
        @Min(0) @Max(200) int labelColumn,
        @NotBlank @Size(max = 100) String dateLabel,
        @Size(max = 100) String weightLabel,
        @Size(max = 100) String stepsLabel,
        @NotNull DateOrder dateOrder
) {
}
