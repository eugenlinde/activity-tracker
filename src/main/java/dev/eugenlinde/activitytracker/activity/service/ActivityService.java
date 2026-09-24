package dev.eugenlinde.activitytracker.activity.service;

import dev.eugenlinde.activitytracker.activity.ActivityRepository;
import dev.eugenlinde.activitytracker.activity.dto.ActivityRequest;
import dev.eugenlinde.activitytracker.activity.dto.ActivityResponse;
import dev.eugenlinde.activitytracker.activity.mapper.ActivityMapper;
import dev.eugenlinde.activitytracker.activity.model.Activity;
import dev.eugenlinde.activitytracker.common.exception.ActivityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
public class ActivityService {
    private final ActivityRepository activityRepository;
    private final ActivityMapper activityMapper;

    public ActivityService(ActivityRepository activityRepository, ActivityMapper activityMapper) {
        this.activityRepository = activityRepository;
        this.activityMapper = activityMapper;
    }

    public ActivityResponse create(ActivityRequest activityRequest) {

        Activity activity = new Activity();
        activity.setActivityType(activityRequest.activityType());
        activity.setDuration(activityRequest.duration());
        activity.setStartedAt(activityRequest.startedAt());
        activity.setNotes(activityRequest.notes());

        return activityMapper
                .mapToActivityResponse(activityRepository.save(activity));
    }

    public List<ActivityResponse> findAll() {
        return activityRepository
                .findAll()
                .stream()
                .map(activityMapper::mapToActivityResponse)
                .toList();
    }

    public ActivityResponse findById(Long id) {

        return activityMapper
                .mapToActivityResponse(activityRepository
                        .findById(id)
                        .orElseThrow(ActivityNotFoundException::new)
                );
    }

    public ActivityResponse updateById(Long id, ActivityRequest activityRequest) {
        Activity activity = activityRepository.findById(id).orElseThrow(ActivityNotFoundException::new);
        activity.setActivityType(activityRequest.activityType());
        activity.setDuration(activityRequest.duration());
        activity.setStartedAt(activityRequest.startedAt());
        activity.setNotes(activityRequest.notes());

        return activityMapper.mapToActivityResponse(activityRepository.saveAndFlush(activity));
    }

    public void deleteById(Long id) {
        Activity activity = activityRepository.findById(id).orElseThrow(ActivityNotFoundException::new);
        activityRepository.delete(activity);
    }
}
