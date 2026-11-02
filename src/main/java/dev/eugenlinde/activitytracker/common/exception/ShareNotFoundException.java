package dev.eugenlinde.activitytracker.common.exception;

public class ShareNotFoundException extends RuntimeException {
    public ShareNotFoundException() {
        super("Share not found");
    }
}
