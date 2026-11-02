package dev.eugenlinde.activitytracker.config;

import dev.eugenlinde.activitytracker.TestcontainersConfiguration;
import dev.eugenlinde.activitytracker.activity.ActivityRepository;
import dev.eugenlinde.activitytracker.activity.model.Activity;
import dev.eugenlinde.activitytracker.activity.model.ActivityType;
import dev.eugenlinde.activitytracker.activity.service.ActivityService;
import dev.eugenlinde.activitytracker.media.service.MediaService;
import dev.eugenlinde.activitytracker.share.service.ShareService;
import dev.eugenlinde.activitytracker.user.UserRepository;
import dev.eugenlinde.activitytracker.user.model.User;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@Transactional
class MethodSecurityWiringTest {
    @Autowired
    private ActivityService activityService;
    @Autowired
    private MediaService mediaService;
    @Autowired
    private ShareService shareService;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private ActivityRepository activityRepository;

    private User owner;
    private User viewer;
    private User stranger;
    private Activity activity;

    @BeforeEach
    void setUp() {
        owner = saveUser("wiring-owner@example.com");
        viewer = saveUser("wiring-viewer@example.com");
        stranger = saveUser("wiring-stranger@example.com");
        activity = new Activity();
        activity.setActivityType(ActivityType.RUNNING);
        activity.setDuration(Duration.ofMinutes(30));
        activity.setStartedAt(Instant.parse("2026-10-01T08:00:00Z"));
        activity.setUser(owner);
        activityRepository.save(activity);
        shareService.create(owner.getId(), viewer.getEmail());
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void findAll_owner_isAllowed() {
        authenticateAs(owner);

        assertEquals(1, activityService.findAll(owner.getId()).size());
    }

    @Test
    void findAll_sharedViewer_isAllowed() {
        authenticateAs(viewer);

        assertEquals(1, activityService.findAll(owner.getId()).size());
    }

    @Test
    void findAll_stranger_isDenied() {
        authenticateAs(stranger);

        assertThrows(AccessDeniedException.class, () -> activityService.findAll(owner.getId()));
    }

    @Test
    void findAllMedia_stranger_isDenied() {
        authenticateAs(stranger);

        assertThrows(AccessDeniedException.class, () -> mediaService.findAllByActivityId(activity.getId()));
    }

    @Test
    void deleteMedia_unknownId_isDenied() {
        authenticateAs(owner);

        assertThrows(AccessDeniedException.class, () -> mediaService.deleteById(Long.MAX_VALUE));
    }

    private User saveUser(String email) {
        User user = new User();
        user.setEmail(email);
        user.setFirstName("First");
        user.setLastName("Last");
        user.setPassword("hash");
        user.setEnabled(true);
        return userRepository.save(user);
    }

    private void authenticateAs(User user) {
        SecurityContextHolder.getContext()
                .setAuthentication(new TestingAuthenticationToken(user.getId().toString(), null, "ROLE_USER"));
    }
}
