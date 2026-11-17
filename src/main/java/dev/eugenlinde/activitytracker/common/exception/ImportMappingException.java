package dev.eugenlinde.activitytracker.common.exception;

public class ImportMappingException extends RuntimeException {
    public ImportMappingException(String message) {
        super(message);
    }

    public ImportMappingException(String message, Throwable cause) {
        super(message, cause);
    }
}
