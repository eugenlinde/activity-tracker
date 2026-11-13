package dev.eugenlinde.activitytracker.common.exception;

public class WeightEntryNotFoundException extends RuntimeException {
    public WeightEntryNotFoundException() {
        super("Weight entry not found");
    }
}
