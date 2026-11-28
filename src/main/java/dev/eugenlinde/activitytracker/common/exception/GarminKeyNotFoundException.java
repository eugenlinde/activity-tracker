package dev.eugenlinde.activitytracker.common.exception;

public class GarminKeyNotFoundException extends RuntimeException {
    public GarminKeyNotFoundException() {
        super("Garmin key not found");
    }
}
