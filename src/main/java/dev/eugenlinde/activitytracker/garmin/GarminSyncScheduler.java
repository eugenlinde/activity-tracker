package dev.eugenlinde.activitytracker.garmin;

import dev.eugenlinde.activitytracker.garmin.service.GarminSyncService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class GarminSyncScheduler {
    private final GarminSyncService garminSyncService;

    public GarminSyncScheduler(GarminSyncService garminSyncService) {
        this.garminSyncService = garminSyncService;
    }

    @Scheduled(cron = "${garmin.sync-cron}", zone = "UTC")
    public void run() {
        garminSyncService.syncAll();
    }
}
