package dev.eugenlinde.activitytracker.common.exception;

public class GarminKeyExistsException extends RuntimeException {
    public GarminKeyExistsException() {
        super("A Garmin key is already added. Remove it first.");
    }
}
