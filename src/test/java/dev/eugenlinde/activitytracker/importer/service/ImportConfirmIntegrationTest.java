package dev.eugenlinde.activitytracker.importer.service;

import dev.eugenlinde.activitytracker.TestcontainersConfiguration;
import dev.eugenlinde.activitytracker.activity.ActivityRepository;
import dev.eugenlinde.activitytracker.activity.model.Exercise;
import dev.eugenlinde.activitytracker.activity.model.Session;
import dev.eugenlinde.activitytracker.activity.model.WorkoutSet;
import dev.eugenlinde.activitytracker.common.exception.InvalidEntryDateException;
import dev.eugenlinde.activitytracker.config.SecurityConfig;
import dev.eugenlinde.activitytracker.importer.dto.ImportConfirmRequest;
import dev.eugenlinde.activitytracker.importer.dto.ImportConfirmResponse;
import dev.eugenlinde.activitytracker.importer.dto.StepsImport;
import dev.eugenlinde.activitytracker.importer.dto.WeightImport;
import dev.eugenlinde.activitytracker.importer.dto.WorkoutImport;
import dev.eugenlinde.activitytracker.steps.StepEntryRepository;
import dev.eugenlinde.activitytracker.user.UserRepository;
import dev.eugenlinde.activitytracker.user.model.User;
import dev.eugenlinde.activitytracker.weight.WeightEntryRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
@Import({TestcontainersConfiguration.class, SecurityConfig.class})
@ActiveProfiles("test")
class ImportConfirmIntegrationTest {

    @Autowired
    private ImportConfirmService importConfirmService;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private ActivityRepository activityRepository;
    @Autowired
    private WeightEntryRepository weightEntryRepository;
    @Autowired
    private StepEntryRepository stepEntryRepository;

    private User user;

    @BeforeEach
    void setUp() {
        User newUser = new User();
        newUser.setEmail("import-confirm@example.com");
        newUser.setFirstName("First");
        newUser.setLastName("Last");
        newUser.setPassword("hash");
        newUser.setEnabled(true);
        user = userRepository.saveAndFlush(newUser);
    }

    @AfterEach
    void cleanUp() {
        activityRepository.deleteAll(activityRepository.findAllByUserId(user.getId()));
        weightEntryRepository.deleteAll(weightEntryRepository.findAllByUserIdAndEntryDateBetweenOrderByEntryDateAsc(
                user.getId(), LocalDate.parse("2000-01-01"), LocalDate.parse("2100-01-01")));
        stepEntryRepository.deleteAll(stepEntryRepository.findAllByUserIdAndEntryDateBetweenOrderByEntryDateAsc(
                user.getId(), LocalDate.parse("2000-01-01"), LocalDate.parse("2100-01-01")));
        userRepository.delete(user);
    }

    private static WorkoutImport workout() {
        return new WorkoutImport(
                Instant.parse("2026-11-03T10:00:00Z"),
                new Session("1", List.of(new Exercise("Kükk", null, null, null, null, List.of(new WorkoutSet(100.0, 8))))),
                "Nädal 1 / Treening 1"
        );
    }

    @Test
    void confirm_writesEverythingAndTheFingerprintIsCountedAfterwards() {
        ImportConfirmResponse response = importConfirmService.confirm(user.getId(), new ImportConfirmRequest(
                List.of(workout()),
                List.of(new WeightImport(LocalDate.parse("2026-11-01"), new BigDecimal("81.3"))),
                List.of(new StepsImport(LocalDate.parse("2026-11-01"), 10234))
        ));

        assertEquals(new ImportConfirmResponse(1, 1, 1), response);
        assertEquals(1, activityRepository.findAllByUserId(user.getId()).size());
        assertEquals(1, activityRepository.countImportFingerprints(user.getId()).size());
        assertEquals(1L, activityRepository.countImportFingerprints(user.getId()).getFirst().getTotal());
        assertEquals(new BigDecimal("81.3"), weightEntryRepository
                .findByUserIdAndEntryDate(user.getId(), LocalDate.parse("2026-11-01")).orElseThrow().getWeightKg());
        assertEquals(10234, stepEntryRepository
                .findByUserIdAndEntryDate(user.getId(), LocalDate.parse("2026-11-01")).orElseThrow().getSteps());
    }

    @Test
    void confirm_whenOneEntryIsRefused_writesNothingAtAll() {
        ImportConfirmRequest request = new ImportConfirmRequest(
                List.of(workout()),
                List.of(new WeightImport(LocalDate.parse("2026-11-01"), new BigDecimal("81.3"))),
                List.of(new StepsImport(LocalDate.parse("2999-01-01"), 10234))
        );

        assertThrows(InvalidEntryDateException.class, () -> importConfirmService.confirm(user.getId(), request));

        assertEquals(0, activityRepository.findAllByUserId(user.getId()).size());
        assertEquals(true, weightEntryRepository
                .findByUserIdAndEntryDate(user.getId(), LocalDate.parse("2026-11-01")).isEmpty());
    }
}
