package dev.eugenlinde.activitytracker.media.dto;

import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;

public record MediaDownload(
        Resource resource,
        MediaType contentType
) {
}
