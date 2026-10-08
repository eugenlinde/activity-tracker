package dev.eugenlinde.activitytracker.share.controller;

import dev.eugenlinde.activitytracker.share.dto.ShareRequest;
import dev.eugenlinde.activitytracker.share.dto.ShareResponse;
import dev.eugenlinde.activitytracker.share.service.ShareService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Objects;

@RestController
@RequestMapping("/api/shares")
public class ShareController {
    private final ShareService shareService;

    public ShareController(ShareService shareService) {
        this.shareService = shareService;
    }

    @PostMapping()
    public ResponseEntity<Void> createShare(
            @RequestBody @Valid ShareRequest shareRequest,
            @AuthenticationPrincipal Jwt jwt
    ) {
        shareService.create(userId(jwt), shareRequest.email());

        return ResponseEntity.status(HttpStatus.CREATED).body(null);
    }

    @GetMapping()
    public ResponseEntity<List<ShareResponse>> getShares(@AuthenticationPrincipal Jwt jwt) {
        List<ShareResponse> userShares = shareService.getUserShares(userId(jwt));

        return ResponseEntity.ok(userShares);
    }

    @GetMapping("/received")
    public ResponseEntity<List<ShareResponse>> getReceivedShares(@AuthenticationPrincipal Jwt jwt) {
        List<ShareResponse> receivedShares = shareService.getUserReceivedShares(userId(jwt));

        return ResponseEntity.ok(receivedShares);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteShare(
            @PathVariable Long id,
            @AuthenticationPrincipal Jwt jwt
    ) {
        shareService.deleteById(id, userId(jwt));

        return ResponseEntity.noContent().build();
    }


    private Long userId(Jwt jwt) {
        return Long.valueOf(Objects.requireNonNull(jwt.getSubject()));
    }

}
