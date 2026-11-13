package dev.eugenlinde.activitytracker.common.exception;

public class StepEntryNotFoundException extends RuntimeException {
    public StepEntryNotFoundException() {
        super("Steps entry not found");
    }
}
