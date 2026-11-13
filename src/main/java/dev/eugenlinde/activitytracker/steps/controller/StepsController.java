package dev.eugenlinde.activitytracker.steps.controller;

import dev.eugenlinde.activitytracker.steps.dto.StepsRequest;
import dev.eugenlinde.activitytracker.steps.dto.StepsResponse;
import dev.eugenlinde.activitytracker.steps.service.StepsService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/steps")
public class StepsController {
    private final StepsService stepsService;

    public StepsController(StepsService stepsService) {
        this.stepsService = stepsService;
    }

    @GetMapping
    public ResponseEntity<List<StepsResponse>> getSteps(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        return ResponseEntity.ok(stepsService.findAll(userId(jwt), from, to));
    }

    @PutMapping("/{date}")
    public ResponseEntity<StepsResponse> putSteps(
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestBody @Valid StepsRequest stepsRequest,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return ResponseEntity.ok(stepsService.upsert(userId(jwt), date, stepsRequest.steps().intValueExact()));
    }

    @DeleteMapping("/{date}")
    public ResponseEntity<Void> deleteSteps(
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @AuthenticationPrincipal Jwt jwt
    ) {
        stepsService.delete(userId(jwt), date);
        return ResponseEntity.noContent().build();
    }

    private Long userId(Jwt jwt) {
        return Long.valueOf(jwt.getSubject());
    }
}
