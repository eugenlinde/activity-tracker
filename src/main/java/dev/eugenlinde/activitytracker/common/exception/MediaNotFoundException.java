package dev.eugenlinde.activitytracker.common.exception;

public class MediaNotFoundException extends RuntimeException {
    public MediaNotFoundException() {
        super("Media not found!");
    }
}
