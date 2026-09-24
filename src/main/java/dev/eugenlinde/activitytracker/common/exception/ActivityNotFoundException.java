package dev.eugenlinde.activitytracker.common.exception;

public class ActivityNotFoundException extends RuntimeException {
    public ActivityNotFoundException() {
        super("Activity does not exist");
    }
}
