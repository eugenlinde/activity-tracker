package dev.eugenlinde.activitytracker.common.exception;

public class ShareExistsException extends RuntimeException {
    public ShareExistsException() {
        super("Share already exists");
    }
}
