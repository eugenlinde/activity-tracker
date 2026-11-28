package dev.eugenlinde.activitytracker.garmin.service;

import dev.eugenlinde.activitytracker.common.EntryDatePolicy;
import dev.eugenlinde.activitytracker.garmin.GarminClient;
import dev.eugenlinde.activitytracker.garmin.GarminConnectionRepository;
import dev.eugenlinde.activitytracker.garmin.GarminException;
import dev.eugenlinde.activitytracker.garmin.model.GarminConnection;
import dev.eugenlinde.activitytracker.garmin.model.GarminSyncStatus;
import dev.eugenlinde.activitytracker.steps.service.StepsService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
public class GarminSyncService {
    private static final int MAX_STEPS = 999_999;

    private final GarminConnectionRepository garminConnectionRepository;
    private final GarminClient garminClient;
    private final StepsService stepsService;
    private final GarminConnectionService garminConnectionService;
    private final EntryDatePolicy entryDatePolicy;
    private final Clock clock;

    public GarminSyncService(
            GarminConnectionRepository garminConnectionRepository,
            GarminClient garminClient,
            StepsService stepsService,
            GarminConnectionService garminConnectionService,
            EntryDatePolicy entryDatePolicy,
            Clock clock
    ) {
        this.garminConnectionRepository = garminConnectionRepository;
        this.garminClient = garminClient;
        this.stepsService = stepsService;
        this.garminConnectionService = garminConnectionService;
        this.entryDatePolicy = entryDatePolicy;
        this.clock = clock;
    }

    public void syncAll() {
        for (Long userId : garminConnectionRepository.findAllUserIds()) {
            syncUser(userId);
        }
    }

    public void syncUser(Long userId) {
        Optional<GarminConnection> found = garminConnectionRepository.findByUserId(userId);
        if (found.isEmpty()) {
            return;
        }
        GarminConnection connection = found.get();
        GarminSyncStatus status;
        try {
            fetchAndSave(userId, connection);
            status = GarminSyncStatus.OK;
        } catch (GarminException failure) {
            log.warn("Garmin sync failed for user {}: {}", userId, failure.getMessage());
            status = GarminSyncStatus.FAILED;
        } catch (Exception failure) {
            log.warn("Garmin sync failed for user {}: {}", userId, failure.getClass().getSimpleName());
            status = GarminSyncStatus.FAILED;
        }
        try {
            garminConnectionService.recordSync(userId, status, clock.instant());
        } catch (Exception failure) {
            log.warn("Garmin sync result not recorded for user {}: {}", userId, failure.getClass().getSimpleName());
        }
    }

    private void fetchAndSave(Long userId, GarminConnection connection) {
        String accessToken = garminClient.fetchAccessToken(connection.getApiKey());
        for (LocalDate date : datesToFetch(connection)) {
            Optional<Integer> steps = garminClient.fetchDailySteps(accessToken, date);
            if (steps.isPresent() && steps.get() > 0 && steps.get() <= MAX_STEPS) {
                stepsService.saveFromGarmin(userId, date, steps.get());
            }
        }
    }

    private List<LocalDate> datesToFetch(GarminConnection connection) {
        LocalDate today = entryDatePolicy.today();
        LocalDate firstAllowed = connection.getCreatedAt().atZone(ZoneOffset.UTC).toLocalDate();
        return List.of(today.minusDays(1), today).stream()
                .filter(date -> !date.isBefore(firstAllowed))
                .toList();
    }
}
