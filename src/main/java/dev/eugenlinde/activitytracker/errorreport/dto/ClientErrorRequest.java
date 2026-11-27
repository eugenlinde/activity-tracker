package dev.eugenlinde.activitytracker.errorreport.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

public record ClientErrorRequest(
        @NotNull
        @Pattern(regexp = "[A-Za-z]{1,60}")
        String name,
        @NotNull
        @Size(min = 1, max = 20)
        List<@NotNull @Size(max = 200) @Pattern(regexp = "[A-Za-z0-9._/-]{1,180}:[0-9]{1,7}:[0-9]{1,7}") String> frames,
        @NotNull
        @Pattern(regexp = "[A-Za-z/:_*-]{1,100}")
        String route
) {
}
