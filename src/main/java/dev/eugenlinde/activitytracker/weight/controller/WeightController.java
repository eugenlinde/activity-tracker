package dev.eugenlinde.activitytracker.weight.controller;

import dev.eugenlinde.activitytracker.weight.dto.WeightRequest;
import dev.eugenlinde.activitytracker.weight.dto.WeightResponse;
import dev.eugenlinde.activitytracker.weight.service.WeightService;
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
@RequestMapping("/api/weights")
public class WeightController {
    private final WeightService weightService;

    public WeightController(WeightService weightService) {
        this.weightService = weightService;
    }

    @GetMapping
    public ResponseEntity<List<WeightResponse>> getWeights(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        return ResponseEntity.ok(weightService.findAll(userId(jwt), from, to));
    }

    @PutMapping("/{date}")
    public ResponseEntity<WeightResponse> putWeight(
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestBody @Valid WeightRequest weightRequest,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return ResponseEntity.ok(weightService.upsert(userId(jwt), date, weightRequest.weightKg()));
    }

    @DeleteMapping("/{date}")
    public ResponseEntity<Void> deleteWeight(
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @AuthenticationPrincipal Jwt jwt
    ) {
        weightService.delete(userId(jwt), date);
        return ResponseEntity.noContent().build();
    }

    private Long userId(Jwt jwt) {
        return Long.valueOf(jwt.getSubject());
    }
}
