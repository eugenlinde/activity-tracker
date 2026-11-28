package dev.eugenlinde.activitytracker.garmin.controller;

import dev.eugenlinde.activitytracker.garmin.dto.GarminKeyRequest;
import dev.eugenlinde.activitytracker.garmin.dto.GarminStatusResponse;
import dev.eugenlinde.activitytracker.garmin.service.GarminConnectionService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Objects;

@RestController
@RequestMapping("/api/garmin")
public class GarminController {
    private final GarminConnectionService garminConnectionService;

    public GarminController(GarminConnectionService garminConnectionService) {
        this.garminConnectionService = garminConnectionService;
    }

    @GetMapping
    public ResponseEntity<GarminStatusResponse> status(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(garminConnectionService.status(userId(jwt)));
    }

    @PostMapping
    public ResponseEntity<GarminStatusResponse> add(
            @RequestBody @Valid GarminKeyRequest request,
            @AuthenticationPrincipal Jwt jwt
    ) {
        GarminStatusResponse response = garminConnectionService.add(userId(jwt), request.apiKey());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @DeleteMapping
    public ResponseEntity<Void> remove(@AuthenticationPrincipal Jwt jwt) {
        garminConnectionService.remove(userId(jwt));
        return ResponseEntity.noContent().build();
    }

    private Long userId(Jwt jwt) {
        return Long.valueOf(Objects.requireNonNull(jwt.getSubject()));
    }
}
