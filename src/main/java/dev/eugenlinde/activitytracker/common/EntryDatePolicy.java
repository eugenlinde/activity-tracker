package dev.eugenlinde.activitytracker.common;

import dev.eugenlinde.activitytracker.common.exception.InvalidEntryDateException;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;

@Component
public class EntryDatePolicy {
    private final Clock clock;

    public EntryDatePolicy(Clock clock) {
        this.clock = clock;
    }

    public LocalDate today() {
        return LocalDate.now(clock);
    }

    public LocalDate latestAllowedDate() {
        return today().plusDays(1);
    }

    public void requireNotInFuture(LocalDate date) {
        if (date.isAfter(latestAllowedDate())) {
            throw new InvalidEntryDateException();
        }
    }
}
