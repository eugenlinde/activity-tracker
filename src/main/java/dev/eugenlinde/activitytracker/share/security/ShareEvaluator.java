package dev.eugenlinde.activitytracker.share.security;

import dev.eugenlinde.activitytracker.media.MediaRepository;
import dev.eugenlinde.activitytracker.media.model.Media;
import dev.eugenlinde.activitytracker.share.ShareRepository;
import org.jspecify.annotations.Nullable;
import org.springframework.security.access.PermissionEvaluator;
import org.springframework.security.core.Authentication;

import java.io.Serializable;

public class ShareEvaluator implements PermissionEvaluator {
    private final ShareRepository shareRepository;
    private final MediaRepository mediaRepository;

    public ShareEvaluator(
            ShareRepository shareRepository,
            MediaRepository mediaRepository
    ) {
        this.shareRepository = shareRepository;
        this.mediaRepository = mediaRepository;
    }

    @Override
    public boolean hasPermission(Authentication authentication, @Nullable Object targetDomainObject, Object permission) {
        return false;
    }

    @Override
    public boolean hasPermission(Authentication authentication, Serializable targetId, String targetType, Object permission) {
        String ALLOWED_PERMISSION = "READ";
        if (!ALLOWED_PERMISSION.equals(permission)) {
            return false;
        }

        Long viewerId = Long.parseLong(authentication.getName());

        if (targetType.equals("ActivityLog")) {
            Long ownerId = Long.valueOf(targetId.toString());

            return canRead(ownerId, viewerId);
        }

        if (targetType.equals("Media")) {
            Media media = mediaRepository.findById(Long.valueOf(targetId.toString())).orElse(null);

            if (media == null) {
                return false;
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
