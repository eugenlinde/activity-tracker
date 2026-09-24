package dev.eugenlinde.activitytracker.activity.controller;

import dev.eugenlinde.activitytracker.activity.dto.ActivityRequest;
import dev.eugenlinde.activitytracker.activity.dto.ActivityResponse;
import dev.eugenlinde.activitytracker.activity.service.ActivityService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
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
    public ResponseEntity<List<ActivityResponse>> getActivities() {
        List<ActivityResponse> activityResponseList = activityService.findAll();

        return ResponseEntity
                .ok(activityResponseList);
    }

    @PostMapping()
    public ResponseEntity<ActivityResponse> createActivity(@RequestBody @Valid ActivityRequest activityRequest) {
        ActivityResponse activityResponse = activityService.create(activityRequest);
        URI location = URI.create("/api/activities/" + activityResponse.id());

        return ResponseEntity
                .created(location)
                .body(activityResponse);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ActivityResponse> getActivity(@PathVariable Long id) {
        ActivityResponse activityResponse = activityService.findById(id);

        return ResponseEntity.ok(activityResponse);
    }

    @PutMapping("/{id}")
    public ResponseEntity<ActivityResponse> updateActivity(@PathVariable Long id, @RequestBody @Valid ActivityRequest activityRequest) {
        ActivityResponse activityResponse = activityService.updateById(id, activityRequest);

        return ResponseEntity.ok(activityResponse);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteActivity(@PathVariable Long id) {
        activityService.deleteById(id);

        return ResponseEntity.noContent().build();
    }

}
