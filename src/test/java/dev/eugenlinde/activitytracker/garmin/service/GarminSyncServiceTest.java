package dev.eugenlinde.activitytracker.garmin.service;

import dev.eugenlinde.activitytracker.common.EntryDatePolicy;
import dev.eugenlinde.activitytracker.garmin.GarminClient;
import dev.eugenlinde.activitytracker.garmin.GarminConnectionRepository;
import dev.eugenlinde.activitytracker.garmin.GarminException;
import dev.eugenlinde.activitytracker.garmin.model.GarminConnection;
import dev.eugenlinde.activitytracker.garmin.model.GarminSyncStatus;
import dev.eugenlinde.activitytracker.steps.service.StepsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class GarminSyncServiceTest {
    private static final Instant NOW = Instant.parse("2026-11-28T10:00:00Z");
    private static final LocalDate TODAY = LocalDate.parse("2026-11-28");
    private static final LocalDate YESTERDAY = LocalDate.parse("2026-11-27");
    private static final Instant ADDED_EARLIER = Instant.parse("2026-11-20T08:00:00Z");

    private final GarminConnectionRepository garminConnectionRepository = mock(GarminConnectionRepository.class);
    private final GarminClient garminClient = mock(GarminClient.class);
    private final StepsService stepsService = mock(StepsService.class);
    private final GarminConnectionService garminConnectionService = mock(GarminConnectionService.class);
    private GarminSyncService service;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        service = new GarminSyncService(
                garminConnectionRepository,
                garminClient,
                stepsService,
                garminConnectionService,
                new EntryDatePolicy(clock),
                clock);
    }

    @Test
    void syncAll_withoutConnections_callsNothing() {
        when(garminConnectionRepository.findAllUserIds()).thenReturn(List.of());

        service.syncAll();

        verifyNoInteractions(garminClient, stepsService, garminConnectionService);
    }

    @Test
    void syncUser_fetchesYesterdayAndToday_andRecordsOk() {
        givenConnection(1L, "key-1", ADDED_EARLIER);
        when(garminClient.fetchAccessToken("key-1")).thenReturn("token-1");
        when(garminClient.fetchDailySteps("token-1", YESTERDAY)).thenReturn(Optional.of(9000));
        when(garminClient.fetchDailySteps("token-1", TODAY)).thenReturn(Optional.of(3000));

        service.syncUser(1L);

        verify(stepsService).saveFromGarmin(1L, YESTERDAY, 9000);
        verify(stepsService).saveFromGarmin(1L, TODAY, 3000);
        verify(garminConnectionService).recordSync(1L, GarminSyncStatus.OK, NOW);
    }

    @Test
    void syncUser_forAKeyAddedToday_fetchesOnlyToday() {
        givenConnection(1L, "key-1", Instant.parse("2026-11-28T00:30:00Z"));
        when(garminClient.fetchAccessToken("key-1")).thenReturn("token-1");
        when(garminClient.fetchDailySteps("token-1", TODAY)).thenReturn(Optional.of(3000));

        service.syncUser(1L);

        verify(garminClient, never()).fetchDailySteps(anyString(), eq(YESTERDAY));
        verify(stepsService).saveFromGarmin(1L, TODAY, 3000);
        verify(garminConnectionService).recordSync(1L, GarminSyncStatus.OK, NOW);
    }

    @Test
    void syncUser_doesNotSaveZeroNegativeOrTooManySteps() {
        givenConnection(1L, "key-1", ADDED_EARLIER);
        when(garminClient.fetchAccessToken("key-1")).thenReturn("token-1");
        when(garminClient.fetchDailySteps("token-1", YESTERDAY)).thenReturn(Optional.of(0));
        when(garminClient.fetchDailySteps("token-1", TODAY)).thenReturn(Optional.of(-5));

        service.syncUser(1L);

        when(garminClient.fetchDailySteps("token-1", YESTERDAY)).thenReturn(Optional.of(1_000_000));
        when(garminClient.fetchDailySteps("token-1", TODAY)).thenReturn(Optional.empty());

        service.syncUser(1L);

        verify(stepsService, never()).saveFromGarmin(any(), any(), anyInt());
        verify(garminConnectionService, org.mockito.Mockito.times(2)).recordSync(1L, GarminSyncStatus.OK, NOW);
    }

    @Test
    void syncUser_savesTheLargestAllowedValue() {
        givenConnection(1L, "key-1", ADDED_EARLIER);
        when(garminClient.fetchAccessToken("key-1")).thenReturn("token-1");
        when(garminClient.fetchDailySteps("token-1", YESTERDAY)).thenReturn(Optional.empty());
        when(garminClient.fetchDailySteps("token-1", TODAY)).thenReturn(Optional.of(999_999));

        service.syncUser(1L);

        verify(stepsService).saveFromGarmin(1L, TODAY, 999_999);
    }

    @Test
    void syncUser_onAManualDay_stillRecordsOk() {
        givenConnection(1L, "key-1", ADDED_EARLIER);
        when(garminClient.fetchAccessToken("key-1")).thenReturn("token-1");
        when(garminClient.fetchDailySteps("token-1", YESTERDAY)).thenReturn(Optional.of(9000));
        when(garminClient.fetchDailySteps("token-1", TODAY)).thenReturn(Optional.empty());
        when(stepsService.saveFromGarmin(1L, YESTERDAY, 9000)).thenReturn(false);

        service.syncUser(1L);

        verify(stepsService).saveFromGarmin(1L, YESTERDAY, 9000);
        verify(garminConnectionService).recordSync(1L, GarminSyncStatus.OK, NOW);
    }

    @Test
    void syncUser_whenTheTokenExchangeFails_recordsFailedAndSavesNothing() {
        givenConnection(1L, "key-1", ADDED_EARLIER);
        when(garminClient.fetchAccessToken("key-1")).thenThrow(new GarminException("Garmin response status: 401"));

        service.syncUser(1L);

        verify(garminClient, never()).fetchDailySteps(anyString(), any());
        verify(stepsService, never()).saveFromGarmin(any(), any(), anyInt());
        verify(garminConnectionService).recordSync(1L, GarminSyncStatus.FAILED, NOW);
    }

    @Test
    void syncUser_whenTheSecondDateFails_keepsTheFirstAndRecordsFailed() {
        givenConnection(1L, "key-1", ADDED_EARLIER);
        when(garminClient.fetchAccessToken("key-1")).thenReturn("token-1");
        when(garminClient.fetchDailySteps("token-1", YESTERDAY)).thenReturn(Optional.of(9000));
        when(garminClient.fetchDailySteps("token-1", TODAY)).thenThrow(new GarminException("Garmin response status: 500"));

        service.syncUser(1L);

        verify(stepsService).saveFromGarmin(1L, YESTERDAY, 9000);
        verify(garminConnectionService).recordSync(1L, GarminSyncStatus.FAILED, NOW);
    }

    @Test
    void syncAll_whenTheFirstUserFails_stillSyncsTheSecond() {
        when(garminConnectionRepository.findAllUserIds()).thenReturn(List.of(1L, 2L));
        givenConnection(1L, "key-1", ADDED_EARLIER);
        givenConnection(2L, "key-2", ADDED_EARLIER);
        when(garminClient.fetchAccessToken("key-1")).thenThrow(new RuntimeException("boom"));
        when(garminClient.fetchAccessToken("key-2")).thenReturn("token-2");
        when(garminClient.fetchDailySteps("token-2", YESTERDAY)).thenReturn(Optional.empty());
        when(garminClient.fetchDailySteps("token-2", TODAY)).thenReturn(Optional.of(4000));

        service.syncAll();

        verify(garminConnectionService).recordSync(1L, GarminSyncStatus.FAILED, NOW);
        verify(stepsService).saveFromGarmin(2L, TODAY, 4000);
        verify(garminConnectionService).recordSync(2L, GarminSyncStatus.OK, NOW);
    }

    @Test
    void syncUser_whenTheConnectionIsGone_callsNothing() {
        when(garminConnectionRepository.findByUserId(1L)).thenReturn(Optional.empty());

        service.syncUser(1L);

        verifyNoInteractions(garminClient, stepsService, garminConnectionService);
    }

    private void givenConnection(Long userId, String apiKey, Instant createdAt) {
        GarminConnection connection = new GarminConnection();
        connection.setApiKey(apiKey);
        ReflectionTestUtils.setField(connection, "createdAt", createdAt);
        when(garminConnectionRepository.findByUserId(userId)).thenReturn(Optional.of(connection));
    }
}
