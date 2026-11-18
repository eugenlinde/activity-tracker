package dev.eugenlinde.activitytracker.importer;

import dev.eugenlinde.activitytracker.common.exception.ImportLimitExceededException;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PreviewLimiterTest {

    private final Clock clock = mock(Clock.class);

    private PreviewLimiter limiter(int limit) {
        when(clock.getZone()).thenReturn(ZoneOffset.UTC);
        when(clock.instant()).thenReturn(Instant.parse("2026-11-17T10:00:00Z"));
        ImportProperties properties = new ImportProperties();
        properties.setDailyPreviewLimit(limit);
        return new PreviewLimiter(properties, clock);
    }

    @Test
    void acquire_upToTheLimit_isAllowedAndTheNextOneIsRefused() {
        PreviewLimiter limiter = limiter(3);

        assertDoesNotThrow(() -> limiter.acquire(7L));
        assertDoesNotThrow(() -> limiter.acquire(7L));
        assertDoesNotThrow(() -> limiter.acquire(7L));
        assertThrows(ImportLimitExceededException.class, () -> limiter.acquire(7L));
    }

    @Test
    void acquire_countsEachUserSeparately() {
        PreviewLimiter limiter = limiter(1);

        limiter.acquire(7L);

        assertDoesNotThrow(() -> limiter.acquire(8L));
        assertThrows(ImportLimitExceededException.class, () -> limiter.acquire(7L));
    }

    @Test
    void acquire_onTheNextDay_startsFromZeroAgain() {
        PreviewLimiter limiter = limiter(1);
        limiter.acquire(7L);

        when(clock.instant()).thenReturn(Instant.parse("2026-11-18T00:00:01Z"));

        assertDoesNotThrow(() -> limiter.acquire(7L));
    }
}
