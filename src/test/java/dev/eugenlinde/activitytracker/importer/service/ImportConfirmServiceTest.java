package dev.eugenlinde.activitytracker.importer.service;

import dev.eugenlinde.activitytracker.activity.ActivityRepository;
import dev.eugenlinde.activitytracker.activity.model.Activity;
import dev.eugenlinde.activitytracker.activity.model.ActivityType;
import dev.eugenlinde.activitytracker.activity.model.Exercise;
import dev.eugenlinde.activitytracker.activity.model.Session;
import dev.eugenlinde.activitytracker.activity.model.WorkoutSet;
import dev.eugenlinde.activitytracker.importer.ImportFingerprint;
import dev.eugenlinde.activitytracker.importer.dto.ImportConfirmRequest;
import dev.eugenlinde.activitytracker.importer.dto.ImportConfirmResponse;
import dev.eugenlinde.activitytracker.importer.dto.StepsImport;
import dev.eugenlinde.activitytracker.importer.dto.WeightImport;
import dev.eugenlinde.activitytracker.importer.dto.WorkoutImport;
import dev.eugenlinde.activitytracker.steps.service.StepsService;
import dev.eugenlinde.activitytracker.user.UserRepository;
import dev.eugenlinde.activitytracker.user.model.User;
import dev.eugenlinde.activitytracker.weight.service.WeightService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ImportConfirmServiceTest {

    private static final Long USER = 7L;

    private final ActivityRepository activityRepository = mock(ActivityRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final WeightService weightService = mock(WeightService.class);
    private final StepsService stepsService = mock(StepsService.class);
    private final ImportConfirmService service =
            new ImportConfirmService(activityRepository, userRepository, weightService, stepsService);

    private static Session session(double weight) {
        return new Session("1", List.of(
                new Exercise("Kükk", "5-8", null, null, null, List.of(new WorkoutSet(weight, 8)))
        ));
    }

    @Test
    void confirm_writesEachWorkoutAsAWeightsActivityOfTheCallerWithItsFingerprint() {
        User user = new User();
        when(userRepository.getReferenceById(USER)).thenReturn(user);
        Instant startedAt = Instant.parse("2026-11-03T10:00:00Z");

        ImportConfirmResponse response = service.confirm(USER, new ImportConfirmRequest(
                List.of(new WorkoutImport(startedAt, session(100.0), "Nädal 1 / Treening 1"),
                        new WorkoutImport(startedAt, session(102.5), null)),
                List.of(),
                List.of()
        ));

        ArgumentCaptor<Activity> saved = ArgumentCaptor.forClass(Activity.class);
        verify(activityRepository, times(2)).save(saved.capture());
        Activity first = saved.getAllValues().getFirst();
        assertSame(user, first.getUser());
        assertEquals(ActivityType.WEIGHTS, first.getActivityType());
        assertEquals(startedAt, first.getStartedAt());
        assertEquals(session(100.0), first.getDetails());
        assertEquals("Nädal 1 / Treening 1", first.getNotes());
        assertEquals(ImportFingerprint.of(session(100.0)), first.getImportFingerprint());
        assertEquals(ImportFingerprint.of(session(102.5)), saved.getAllValues().getLast().getImportFingerprint());
        assertEquals(new ImportConfirmResponse(2, 0, 0), response);
        verifyNoInteractions(weightService, stepsService);
    }

    @Test
    void confirm_writesWeightAndStepsThroughTheSameServicesAsManualEntry() {
        ImportConfirmResponse response = service.confirm(USER, new ImportConfirmRequest(
                List.of(),
                List.of(new WeightImport(LocalDate.parse("2026-11-01"), new BigDecimal("81.3")),
                        new WeightImport(LocalDate.parse("2026-11-02"), new BigDecimal("81.1"))),
                List.of(new StepsImport(LocalDate.parse("2026-11-01"), 10234))
        ));

        verify(weightService).upsert(USER, LocalDate.parse("2026-11-01"), new BigDecimal("81.3"));
        verify(weightService).upsert(USER, LocalDate.parse("2026-11-02"), new BigDecimal("81.1"));
        verify(stepsService).upsert(USER, LocalDate.parse("2026-11-01"), 10234);
        assertEquals(new ImportConfirmResponse(0, 2, 1), response);
        verifyNoInteractions(activityRepository);
    }
}
