package dev.eugenlinde.activitytracker.media;

import dev.eugenlinde.activitytracker.media.dto.MediaDownload;
import dev.eugenlinde.activitytracker.media.service.MediaService;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/media")
public class MediaController {

    private final MediaService mediaService;

    public MediaController(
            MediaService mediaService
    ) {
        this.mediaService = mediaService;
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasPermission(#id, 'Media', 'READ')")
    public ResponseEntity<Resource> getMedia(
            @PathVariable Long id,
            @AuthenticationPrincipal Jwt jwt
    ) {
        MediaDownload mediaDownload = mediaService.getMedia(id);

        return ResponseEntity.ok().contentType(mediaDownload.contentType()).body(mediaDownload.resource());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        mediaService.deleteById(id);

        return ResponseEntity.noContent().build();
    }
}
