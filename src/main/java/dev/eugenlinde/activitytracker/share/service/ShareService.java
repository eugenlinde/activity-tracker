package dev.eugenlinde.activitytracker.share.service;

import dev.eugenlinde.activitytracker.common.exception.SelfShareException;
import dev.eugenlinde.activitytracker.common.exception.ShareExistsException;
import dev.eugenlinde.activitytracker.common.exception.ShareNotFoundException;
import dev.eugenlinde.activitytracker.common.exception.UserNotFoundException;
import dev.eugenlinde.activitytracker.share.ShareRepository;
import dev.eugenlinde.activitytracker.share.dto.ShareResponse;
import dev.eugenlinde.activitytracker.share.model.Share;
import dev.eugenlinde.activitytracker.user.UserRepository;
import dev.eugenlinde.activitytracker.user.model.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
public class ShareService {
    private final ShareRepository shareRepository;
    private final UserRepository userRepository;

    public ShareService(ShareRepository shareRepository, UserRepository userRepository) {
        this.shareRepository = shareRepository;
        this.userRepository = userRepository;
    }

    public void create(Long ownerId, String viewerEmail) {
        User viewer = userRepository.findByEmail(viewerEmail).orElseThrow(UserNotFoundException::new);
        if (viewer.getId().equals(ownerId)) {
            throw new SelfShareException();
        }
        if (shareRepository.existsByOwnerIdAndViewerId(ownerId, viewer.getId())) {
            throw new ShareExistsException();
        }
        Share share = new Share();
        share.setOwner(userRepository.getReferenceById(ownerId));
        share.setViewer(viewer);
        shareRepository.save(share);
    }

    @Transactional(readOnly = true)
    public List<ShareResponse> getUserShares(Long ownerId) {
        List<Share> userShares = shareRepository.findAllByOwnerId(ownerId);

        return userShares
                .stream()
                .map(share -> new ShareResponse(
                        share.getId(),
                        share.getOwner().getId(),
                        share.getViewer().getEmail()
                ))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ShareResponse> getUserReceivedShares(Long viewerId) {
        List<Share> userShares = shareRepository.findAllByViewerId(viewerId);

        return userShares
                .stream()
                .map(share -> new ShareResponse(
                        share.getId(),
                        share.getOwner().getId(),
                        share.getOwner().getEmail()
                ))
                .toList();
    }

    public void deleteById(Long id, Long ownerId) {
        Share share = shareRepository.findByIdAndOwnerId(id, ownerId).orElseThrow(ShareNotFoundException::new);
        shareRepository.delete(share);
    }
}
