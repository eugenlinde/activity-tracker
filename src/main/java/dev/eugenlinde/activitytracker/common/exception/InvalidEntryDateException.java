package dev.eugenlinde.activitytracker.common.exception;

public class InvalidEntryDateException extends RuntimeException {
    public InvalidEntryDateException() {
        super("Entry date cannot be in the future");
    }
}
