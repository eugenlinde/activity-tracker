package dev.eugenlinde.activitytracker.common.exception;

public class ImportLimitExceededException extends RuntimeException {
    public ImportLimitExceededException(String message) {
        super(message);
    }
}
