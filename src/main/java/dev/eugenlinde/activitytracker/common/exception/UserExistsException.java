package dev.eugenlinde.activitytracker.common.exception;

public class UserExistsException extends RuntimeException {
    public UserExistsException() {
        super("User already exists");
    }
}
