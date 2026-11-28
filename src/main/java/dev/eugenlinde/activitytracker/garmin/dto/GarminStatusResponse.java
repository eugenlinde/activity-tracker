package dev.eugenlinde.activitytracker.garmin.dto;

import dev.eugenlinde.activitytracker.garmin.model.GarminConnection;
import dev.eugenlinde.activitytracker.garmin.model.GarminSyncStatus;

import java.time.Instant;

public record GarminStatusResponse(
        boolean connected,
        Instant connectedAt,
        Instant lastSyncAt,
        GarminSyncStatus lastSyncStatus
) {
    public static GarminStatusResponse from(GarminConnection connection) {
        return new GarminStatusResponse(
                true,
                connection.getCreatedAt(),
                connection.getLastSyncAt(),
                connection.getLastSyncStatus());
    }

    public static GarminStatusResponse notConnected() {
        return new GarminStatusResponse(false, null, null, null);
    }
}
