package dev.eugenlinde.activitytracker.share.security;

import dev.eugenlinde.activitytracker.activity.ActivityRepository;
import dev.eugenlinde.activitytracker.activity.model.Activity;
import dev.eugenlinde.activitytracker.media.MediaRepository;
import dev.eugenlinde.activitytracker.media.model.Media;
import dev.eugenlinde.activitytracker.share.ShareRepository;
import org.jspecify.annotations.Nullable;
import org.springframework.security.access.PermissionEvaluator;
import org.springframework.security.core.Authentication;

import java.io.Serializable;
import java.util.List;

public class ShareEvaluator implements PermissionEvaluator {
    private final static List<String> ALLOWED_PERMISSIONS = List.of("READ", "DELETE");
    private final ShareRepository shareRepository;
    private final MediaRepository mediaRepository;
    private final ActivityRepository activityRepository;

    public ShareEvaluator(
            ShareRepository shareRepository,
            MediaRepository mediaRepository,
            ActivityRepository activityRepository
    ) {
        this.shareRepository = shareRepository;
        this.mediaRepository = mediaRepository;
        this.activityRepository = activityRepository;
    }

    @Override
    public boolean hasPermission(Authentication authentication, @Nullable Object targetDomainObject, Object permission) {
        return false;
    }

    @Override
    public boolean hasPermission(Authentication authentication, Serializable targetId, String targetType, Object permission) {
        if (!(permission instanceof String) || !ALLOWED_PERMISSIONS.contains(permission)) {
            return false;
        }

        Long viewerId = Long.parseLong(authentication.getName());

        if (targetType.equals("Activity")) {
            Long activityId = Long.valueOf(targetId.toString());
            Activity activity = activityRepository.findById(activityId).orElse(null);

            if (activity == null) {
                return false;
            }

            Long ownerId = activity.getUser().getId();

            return canRead(ownerId, viewerId);
        }

        if (targetType.equals("ActivityLog")) {
            Long ownerId = Long.valueOf(targetId.toString());

            return canRead(ownerId, viewerId);
        }

        if (targetType.equals("Media")) {
            Long mediaId = Long.valueOf(targetId.toString());
            Media media = mediaRepository.findById(mediaId).orElse(null);

            if (media == null) {
                return false;
            }

            if (permission.equals("DELETE")) {
                Activity activity = media.getActivity();

                Long ownerId = activity.getUser().getId();

                return ownerId.equals(viewerId);
            }

            Long ownerId = media.getActivity().getUser().getId();

            return canRead(ownerId, viewerId);
        }

        return false;
    }

    private boolean canRead(Long ownerId, Long viewerId) {
        if (ownerId.equals(viewerId)) {
            return true;
        }

        return shareRepository.existsByOwnerIdAndViewerId(ownerId, viewerId);
    }
}
