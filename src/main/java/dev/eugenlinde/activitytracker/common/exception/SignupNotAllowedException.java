package dev.eugenlinde.activitytracker.common.exception;

public class SignupNotAllowedException extends RuntimeException {
    public SignupNotAllowedException() {
        super("Sign-up is by invitation only");
    }
}
