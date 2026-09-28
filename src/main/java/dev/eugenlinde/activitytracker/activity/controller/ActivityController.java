package dev.eugenlinde.activitytracker.activity.controller;

import dev.eugenlinde.activitytracker.activity.dto.ActivityRequest;
import dev.eugenlinde.activitytracker.activity.dto.ActivityResponse;
import dev.eugenlinde.activitytracker.activity.service.ActivityService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/activities")
public class ActivityController {
    private final ActivityService activityService;

    public ActivityController(ActivityService activityService) {
        this.activityService = activityService;
    }

    @GetMapping()
    public ResponseEntity<List<ActivityResponse>> getActivities(@AuthenticationPrincipal Jwt jwt) {
        List<ActivityResponse> activityResponseList = activityService.findAll(userId(jwt));

        return ResponseEntity
                .ok(activityResponseList);
    }

    @PostMapping()
    public ResponseEntity<ActivityResponse> createActivity(
            @RequestBody @Valid ActivityRequest activityRequest,
            @AuthenticationPrincipal Jwt jwt
    ) {
        ActivityResponse activityResponse = activityService.create(activityRequest, userId(jwt));
        URI location = URI.create("/api/activities/" + activityResponse.id());

        return ResponseEntity
                .created(location)
                .body(activityResponse);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ActivityResponse> getActivity(
            @PathVariable Long id,
            @AuthenticationPrincipal Jwt jwt
    ) {
        ActivityResponse activityResponse = activityService.findById(id, userId(jwt));

        return ResponseEntity.ok(activityResponse);
    }

    @PutMapping("/{id}")
    public ResponseEntity<ActivityResponse> updateActivity(
            @PathVariable Long id,
            @RequestBody @Valid ActivityRequest activityRequest,
            @AuthenticationPrincipal Jwt jwt
    ) {
        ActivityResponse activityResponse = activityService.updateById(id, activityRequest, userId(jwt));

        return ResponseEntity.ok(activityResponse);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteActivity(
            @PathVariable Long id,
            @AuthenticationPrincipal Jwt jwt
    ) {
        activityService.deleteById(id, userId(jwt));

        return ResponseEntity.noContent().build();
    }

    private Long userId(Jwt jwt) {
        return Long.valueOf(Long.valueOf(jwt.getSubject()));
    }
}
