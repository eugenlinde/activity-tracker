package dev.eugenlinde.activitytracker.common.exception;

public class DietEntryNotFoundException extends RuntimeException {
    public DietEntryNotFoundException() {
        super("Diet entry not found");
    }
}
