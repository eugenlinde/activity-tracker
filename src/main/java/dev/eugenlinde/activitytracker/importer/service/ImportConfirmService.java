package dev.eugenlinde.activitytracker.importer.service;

import dev.eugenlinde.activitytracker.activity.ActivityRepository;
import dev.eugenlinde.activitytracker.activity.model.Activity;
import dev.eugenlinde.activitytracker.activity.model.ActivityType;
import dev.eugenlinde.activitytracker.importer.ImportFingerprint;
import dev.eugenlinde.activitytracker.importer.dto.ImportConfirmRequest;
import dev.eugenlinde.activitytracker.importer.dto.ImportConfirmResponse;
import dev.eugenlinde.activitytracker.importer.dto.StepsImport;
import dev.eugenlinde.activitytracker.importer.dto.WeightImport;
import dev.eugenlinde.activitytracker.importer.dto.WorkoutImport;
import dev.eugenlinde.activitytracker.steps.service.StepsService;
import dev.eugenlinde.activitytracker.user.UserRepository;
import dev.eugenlinde.activitytracker.weight.service.WeightService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ImportConfirmService {

    private final ActivityRepository activityRepository;
    private final UserRepository userRepository;
    private final WeightService weightService;
    private final StepsService stepsService;

    public ImportConfirmService(
            ActivityRepository activityRepository,
            UserRepository userRepository,
            WeightService weightService,
            StepsService stepsService
    ) {
        this.activityRepository = activityRepository;
        this.userRepository = userRepository;
        this.weightService = weightService;
        this.stepsService = stepsService;
    }

    @Transactional
    public ImportConfirmResponse confirm(Long userId, ImportConfirmRequest request) {
        for (WorkoutImport workout : request.workouts()) {
            Activity activity = new Activity();
            activity.setUser(userRepository.getReferenceById(userId));
            activity.setActivityType(ActivityType.WEIGHTS);
            activity.setStartedAt(workout.startedAt());
            activity.setDetails(workout.details());
            activity.setNotes(workout.notes());
            activity.setImportFingerprint(ImportFingerprint.of(workout.details()));
            activityRepository.save(activity);
        }
        for (WeightImport weight : request.weights()) {
            weightService.upsert(userId, weight.date(), weight.weightKg());
        }
        for (StepsImport steps : request.steps()) {
            stepsService.upsert(userId, steps.date(), steps.steps());
        }
        return new ImportConfirmResponse(request.workouts().size(), request.weights().size(), request.steps().size());
    }
}
