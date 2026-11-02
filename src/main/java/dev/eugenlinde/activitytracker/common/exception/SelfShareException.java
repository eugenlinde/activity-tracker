package dev.eugenlinde.activitytracker.common.exception;

public class SelfShareException extends RuntimeException {
    public SelfShareException() {
        super("Cannot share a log with yourself");
    }
}
