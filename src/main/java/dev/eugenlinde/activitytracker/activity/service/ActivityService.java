package dev.eugenlinde.activitytracker.activity.service;

import dev.eugenlinde.activitytracker.activity.ActivityRepository;
import dev.eugenlinde.activitytracker.activity.dto.ActivityRequest;
import dev.eugenlinde.activitytracker.activity.dto.ActivityResponse;
import dev.eugenlinde.activitytracker.activity.mapper.ActivityMapper;
import dev.eugenlinde.activitytracker.activity.model.Activity;
import dev.eugenlinde.activitytracker.common.exception.ActivityNotFoundException;
import dev.eugenlinde.activitytracker.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
public class ActivityService {
    private final ActivityRepository activityRepository;
    private final ActivityMapper activityMapper;
    private final UserRepository userRepository;

    public ActivityService(
            ActivityRepository activityRepository,
            ActivityMapper activityMapper,
            UserRepository userRepository
    ) {
        this.activityRepository = activityRepository;
        this.activityMapper = activityMapper;
        this.userRepository = userRepository;
    }

    public ActivityResponse create(ActivityRequest activityRequest, Long userId) {

        Activity activity = new Activity();
        activity.setActivityType(activityRequest.activityType());
        activity.setDuration(activityRequest.duration());
        activity.setStartedAt(activityRequest.startedAt());
        activity.setNotes(activityRequest.notes());
        activity.setUser(userRepository.getReferenceById(userId));

        return activityMapper
                .mapToActivityResponse(activityRepository.save(activity));
    }

    public List<ActivityResponse> findAll(Long userId) {
        return activityRepository
                .findAllByUserId(userId)
                .stream()
                .map(activityMapper::mapToActivityResponse)
                .toList();
    }

    public ActivityResponse findById(Long id, Long userId) {

        return activityMapper
                .mapToActivityResponse(activityRepository
                        .findByIdAndUserId(id, userId)
                        .orElseThrow(ActivityNotFoundException::new)
                );
    }

    public ActivityResponse updateById(Long id, ActivityRequest activityRequest, Long userId) {
        Activity activity = activityRepository
                .findByIdAndUserId(id, userId)
                .orElseThrow(ActivityNotFoundException::new);
        activity.setActivityType(activityRequest.activityType());
        activity.setDuration(activityRequest.duration());
        activity.setStartedAt(activityRequest.startedAt());
        activity.setNotes(activityRequest.notes());

        return activityMapper.mapToActivityResponse(activityRepository.saveAndFlush(activity));
    }

    public void deleteById(Long id, Long userId) {
        Activity activity = activityRepository.findByIdAndUserId(id, userId).orElseThrow(ActivityNotFoundException::new);
        activityRepository.delete(activity);
    }
}
