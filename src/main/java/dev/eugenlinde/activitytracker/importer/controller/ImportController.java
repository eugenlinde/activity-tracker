package dev.eugenlinde.activitytracker.importer.controller;

import dev.eugenlinde.activitytracker.importer.dto.ImportConfirmRequest;
import dev.eugenlinde.activitytracker.importer.dto.ImportConfirmResponse;
import dev.eugenlinde.activitytracker.importer.dto.ImportPreviewResponse;
import dev.eugenlinde.activitytracker.importer.service.ImportConfirmService;
import dev.eugenlinde.activitytracker.importer.service.ImportPreviewService;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@RestController
@RequestMapping("/api/imports")
public class ImportController {

    private final ImportPreviewService importPreviewService;
    private final ImportConfirmService importConfirmService;

    public ImportController(ImportPreviewService importPreviewService, ImportConfirmService importConfirmService) {
        this.importPreviewService = importPreviewService;
        this.importConfirmService = importConfirmService;
    }

    @PostMapping(value = "/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ImportPreviewResponse> preview(
            @RequestPart("file") MultipartFile file,
            @AuthenticationPrincipal Jwt jwt
    ) throws IOException {
        return ResponseEntity.ok(importPreviewService.preview(userId(jwt), file.getOriginalFilename(), file.getBytes()));
    }

    @PostMapping("/confirm")
    public ResponseEntity<ImportConfirmResponse> confirm(
            @RequestBody @Valid ImportConfirmRequest importConfirmRequest,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return ResponseEntity.ok(importConfirmService.confirm(userId(jwt), importConfirmRequest));
    }

    private Long userId(Jwt jwt) {
        return Long.valueOf(jwt.getSubject());
    }
}
