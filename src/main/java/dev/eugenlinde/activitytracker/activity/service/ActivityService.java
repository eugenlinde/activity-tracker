package dev.eugenlinde.activitytracker.activity.service;

import dev.eugenlinde.activitytracker.activity.ActivityRepository;
import dev.eugenlinde.activitytracker.activity.dto.ActivityRequest;
import dev.eugenlinde.activitytracker.activity.dto.ActivityResponse;
import dev.eugenlinde.activitytracker.activity.mapper.ActivityMapper;
import dev.eugenlinde.activitytracker.activity.model.Activity;
import dev.eugenlinde.activitytracker.common.exception.ActivityNotFoundException;
import dev.eugenlinde.activitytracker.media.service.MediaService;
import dev.eugenlinde.activitytracker.user.UserRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
public class ActivityService {
    private final ActivityRepository activityRepository;
    private final ActivityMapper activityMapper;
    private final UserRepository userRepository;
    private final MediaService mediaService;

    public ActivityService(
            ActivityRepository activityRepository,
            ActivityMapper activityMapper,
            UserRepository userRepository,
            MediaService mediaService
    ) {
        this.activityRepository = activityRepository;
        this.activityMapper = activityMapper;
        this.userRepository = userRepository;
        this.mediaService = mediaService;
    }

    public ActivityResponse create(ActivityRequest activityRequest, Long userId) {

        Activity activity = new Activity();
        activity.setActivityType(activityRequest.activityType());
        activity.setDetails(activityRequest.details());
        activity.setDuration(activityRequest.duration());
        activity.setStartedAt(activityRequest.startedAt());
        activity.setNotes(activityRequest.notes());
        activity.setUser(userRepository.getReferenceById(userId));

        return activityMapper
                .mapToActivityResponse(activityRepository.save(activity));
    }

    @PreAuthorize("hasPermission(#ownerId, 'ActivityLog', 'READ')")
    public List<ActivityResponse> findAll(Long ownerId) {

        return activityRepository
                .findAllByUserId(ownerId)
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
        activity.setDetails(activityRequest.details());
        activity.setActivityType(activityRequest.activityType());
        activity.setDuration(activityRequest.duration());
        activity.setStartedAt(activityRequest.startedAt());
        activity.setNotes(activityRequest.notes());

        return activityMapper.mapToActivityResponse(activityRepository.saveAndFlush(activity));
    }

    public void deleteById(Long id, Long userId) {
        Activity activity = activityRepository.findByIdAndUserId(id, userId).orElseThrow(ActivityNotFoundException::new);
        mediaService.deleteAllForActivity(id);
        activityRepository.delete(activity);
    }
}
