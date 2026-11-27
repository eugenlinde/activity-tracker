package dev.eugenlinde.activitytracker.diet.controller;

import dev.eugenlinde.activitytracker.diet.dto.DietRequest;
import dev.eugenlinde.activitytracker.diet.dto.DietResponse;
import dev.eugenlinde.activitytracker.diet.service.DietService;
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
@RequestMapping("/api/diet")
public class DietController {
    private final DietService dietService;

    public DietController(DietService dietService) {
        this.dietService = dietService;
    }

    @GetMapping
    public ResponseEntity<List<DietResponse>> getDiet(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        return ResponseEntity.ok(dietService.findAll(userId(jwt), from, to));
    }

    @PutMapping("/{date}")
    public ResponseEntity<DietResponse> putDiet(
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestBody @Valid DietRequest dietRequest,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return ResponseEntity.ok(
                dietService.upsert(userId(jwt), date, dietRequest.status(), dietRequest.comment()));
    }

    @DeleteMapping("/{date}")
    public ResponseEntity<Void> deleteDiet(
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @AuthenticationPrincipal Jwt jwt
    ) {
        dietService.delete(userId(jwt), date);
        return ResponseEntity.noContent().build();
    }

    private Long userId(Jwt jwt) {
        return Long.valueOf(jwt.getSubject());
    }
}
