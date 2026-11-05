package dev.eugenlinde.activitytracker.activity.dto;

import dev.eugenlinde.activitytracker.activity.model.ActivityType;
import dev.eugenlinde.activitytracker.activity.model.Exercise;
import dev.eugenlinde.activitytracker.activity.model.Session;
import dev.eugenlinde.activitytracker.activity.model.WorkoutSet;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static dev.eugenlinde.activitytracker.activity.ActivityDetailsFixtures.exercise;
import static dev.eugenlinde.activitytracker.activity.ActivityDetailsFixtures.session;
import static dev.eugenlinde.activitytracker.activity.ActivityDetailsFixtures.sessionWithExercises;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ActivityRequestValidationTest {

    private static ValidatorFactory validatorFactory;
    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
        validator = validatorFactory.getValidator();
    }

    @AfterAll
    static void closeValidator() {
        validatorFactory.close();
    }

    @Test
    void validDetails_hasNoViolations() {
        assertTrue(validator.validate(request(session())).isEmpty());
    }

    @Test
    void nullDetailsAndNullDuration_areAllowed() {
        ActivityRequest request = new ActivityRequest(
                ActivityType.WEIGHTS,
                Instant.parse("2026-10-01T08:00:00Z"),
                null,
                null,
                null
        );

        assertTrue(validator.validate(request).isEmpty());
    }

    @Test
    void blankExerciseName_isReportedOnNestedPath() {
        Session details = sessionWithExercises(List.of(exercise(" ", List.of())));

        assertEquals(Set.of("details.exerciseList[0].name"), violationPaths(request(details)));
    }

    @Test
    void negativeRepsInSet_isReportedOnNestedPath() {
        Session details = sessionWithExercises(List.of(
                exercise("Säär smith kang turjal", List.of(new WorkoutSet(100.0, -1)))
        ));

        assertEquals(
                Set.of("details.exerciseList[0].workoutSetList[0].reps"),
                violationPaths(request(details))
        );
    }

    @Test
    void negativeWeightInSet_isReportedOnNestedPath() {
        Session details = sessionWithExercises(List.of(
                exercise("Säär smith kang turjal", List.of(new WorkoutSet(-2.5, 10)))
        ));

        assertEquals(
                Set.of("details.exerciseList[0].workoutSetList[0].weight"),
                violationPaths(request(details))
        );
    }

    @Test
    void blankVersion_isRejected() {
        Session details = new Session(" ", session().exerciseList());

        assertEquals(Set.of("details.version"), violationPaths(request(details)));
    }

    @Test
    void tooManyExercises_isRejected() {
        Exercise exercise = exercise("Bulgaaria kükk", List.of());
        Session details = sessionWithExercises(Collections.nCopies(13, exercise));

        assertEquals(Set.of("details.exerciseList"), violationPaths(request(details)));
    }

    @Test
    void tooManySetsInExercise_isRejected() {
        Session details = sessionWithExercises(List.of(
                exercise("Bulgaaria kükk", Collections.nCopies(13, new WorkoutSet(8.0, 10)))
        ));

        assertEquals(
                Set.of("details.exerciseList[0].workoutSetList"),
                violationPaths(request(details))
        );
    }

    private static ActivityRequest request(Session details) {
        return new ActivityRequest(
                ActivityType.WEIGHTS,
                Instant.parse("2026-10-01T08:00:00Z"),
                null,
                details,
                null
        );
    }

    private static Set<String> violationPaths(ActivityRequest request) {
        return validator.validate(request).stream()
                .map(ConstraintViolation::getPropertyPath)
                .map(Object::toString)
                .collect(Collectors.toSet());
    }
}
