package dev.eugenlinde.activitytracker.importer;

import dev.eugenlinde.activitytracker.common.exception.ImportLimitExceededException;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

@Component
public class PreviewLimiter {

    private final ImportProperties importProperties;
    private final Clock clock;
    private final Map<Long, Integer> usedToday = new HashMap<>();
    private LocalDate day;

    public PreviewLimiter(ImportProperties importProperties, Clock clock) {
        this.importProperties = importProperties;
        this.clock = clock;
    }

    public synchronized void acquire(Long userId) {
        LocalDate today = LocalDate.now(clock);
        if (!today.equals(day)) {
            day = today;
            usedToday.clear();
        }
        int used = usedToday.getOrDefault(userId, 0);
        if (used >= importProperties.getDailyPreviewLimit()) {
            throw new ImportLimitExceededException(
                    "Import limit reached: " + importProperties.getDailyPreviewLimit() + " files a day. Try again tomorrow.");
        }
        usedToday.put(userId, used + 1);
    }
}
