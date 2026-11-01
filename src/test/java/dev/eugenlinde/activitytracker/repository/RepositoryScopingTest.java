package dev.eugenlinde.activitytracker.repository;

import dev.eugenlinde.activitytracker.TestcontainersConfiguration;
import dev.eugenlinde.activitytracker.activity.ActivityRepository;
import dev.eugenlinde.activitytracker.activity.model.Activity;
import dev.eugenlinde.activitytracker.activity.model.ActivityType;
import dev.eugenlinde.activitytracker.share.ShareRepository;
import dev.eugenlinde.activitytracker.share.model.Share;
import dev.eugenlinde.activitytracker.user.model.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class RepositoryScopingTest {
    @Autowired
    private TestEntityManager entityManager;
    @Autowired
    private ActivityRepository activityRepository;
    @Autowired
    private ShareRepository shareRepository;

    private User owner;
    private User viewer;
    private User stranger;
    private Activity ownerActivity;
    private Activity strangerActivity;

    @BeforeEach
    void setUp() {
        owner = persistUser("owner@example.com");
        viewer = persistUser("viewer@example.com");
        stranger = persistUser("stranger@example.com");
        ownerActivity = persistActivity(owner);
        strangerActivity = persistActivity(stranger);
    }

    @Test
    void findAllByUserId_returnsOnlyThatUsersActivities() {
        List<Activity> activities = activityRepository.findAllByUserId(owner.getId());

        assertEquals(List.of(ownerActivity.getId()), activities.stream().map(Activity::getId).toList());
    }

    @Test
    void findByIdAndUserId_whenActivityBelongsToAnotherUser_returnsEmpty() {
        assertTrue(activityRepository.findByIdAndUserId(strangerActivity.getId(), owner.getId()).isEmpty());
    }

    @Test
    void findByIdAndUserId_whenActivityBelongsToUser_returnsIt() {
        assertTrue(activityRepository.findByIdAndUserId(ownerActivity.getId(), owner.getId()).isPresent());
    }

    @Test
    void existsByOwnerIdAndViewerId_whenShareActive_returnsTrue() {
        persistShare(owner, viewer);

        assertTrue(shareRepository.existsByOwnerIdAndViewerId(owner.getId(), viewer.getId()));
    }

    @Test
    void existsByOwnerIdAndViewerId_isDirectional() {
        persistShare(owner, viewer);

        assertFalse(shareRepository.existsByOwnerIdAndViewerId(viewer.getId(), owner.getId()));
    }

    @Test
    void existsByOwnerIdAndViewerId_whenNoShare_returnsFalse() {
        persistShare(owner, viewer);

        assertFalse(shareRepository.existsByOwnerIdAndViewerId(owner.getId(), stranger.getId()));
    }

    @Test
    void existsByOwnerIdAndViewerId_whenShareRevoked_returnsFalse() {
        Share share = persistShare(owner, viewer);

        revoke(share);

        assertFalse(shareRepository.existsByOwnerIdAndViewerId(owner.getId(), viewer.getId()));
    }

    @Test
    void findAllByViewerIdAndFindAllByOwnerId_excludeRevokedShares() {
        Share share = persistShare(owner, viewer);

        revoke(share);

        assertTrue(shareRepository.findAllByViewerId(viewer.getId()).isEmpty());
        assertTrue(shareRepository.findAllByOwnerId(owner.getId()).isEmpty());
    }

    @Test
    void regrantAfterRevoke_isAllowedAndActive() {
        revoke(persistShare(owner, viewer));

        persistShare(owner, viewer);

        assertTrue(shareRepository.existsByOwnerIdAndViewerId(owner.getId(), viewer.getId()));
    }

    private User persistUser(String email) {
        User user = new User();
        user.setEmail(email);
        user.setFirstName("First");
        user.setLastName("Last");
        user.setPassword("hash");
        user.setEnabled(true);
        return entityManager.persist(user);
    }

    private Activity persistActivity(User user) {
        Activity activity = new Activity();
        activity.setActivityType(ActivityType.RUNNING);
        activity.setDuration(Duration.ofMinutes(30));
        activity.setStartedAt(Instant.parse("2026-10-01T08:00:00Z"));
        activity.setUser(user);
        return entityManager.persist(activity);
    }

    private Share persistShare(User shareOwner, User shareViewer) {
        Share share = new Share();
        share.setOwner(shareOwner);
        share.setViewer(shareViewer);
        Share persisted = entityManager.persistAndFlush(share);
        entityManager.clear();
        return persisted;
    }

    private void revoke(Share share) {
        shareRepository.deleteById(share.getId());
        entityManager.flush();
        entityManager.clear();
    }
}
