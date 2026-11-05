package dev.eugenlinde.activitytracker.activity.controller;

import dev.eugenlinde.activitytracker.activity.ActivityRepository;
import dev.eugenlinde.activitytracker.activity.dto.ActivityRequest;
import dev.eugenlinde.activitytracker.activity.dto.ActivityResponse;
import dev.eugenlinde.activitytracker.activity.model.ActivityType;
import dev.eugenlinde.activitytracker.activity.service.ActivityService;
import dev.eugenlinde.activitytracker.common.exception.ActivityNotFoundException;
import dev.eugenlinde.activitytracker.config.SecurityConfig;
import dev.eugenlinde.activitytracker.media.MediaRepository;
import dev.eugenlinde.activitytracker.media.service.MediaService;
import dev.eugenlinde.activitytracker.share.ShareRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.time.Instant;

import static dev.eugenlinde.activitytracker.activity.ActivityDetailsFixtures.session;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;


@WebMvcTest(ActivityController.class)
@Import(SecurityConfig.class)
@ActiveProfiles("test")
class ActivityControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @MockitoBean
    private ActivityService activityService;
    @MockitoBean
    private MediaService mediaService;
    @MockitoBean
    private ShareRepository shareRepository;
    @MockitoBean
    private MediaRepository mediaRepository;
    @MockitoBean
    private ActivityRepository activityRepository;
    @Autowired
    private ObjectMapper objectMapper;
    private Long userId = 1L;
    private Long activityId = 1L;

    private static JwtRequestPostProcessor authenticatedAs(Long userId) {
        return jwt()
                .jwt(builder -> builder.subject(String.valueOf(userId)))
                .authorities(new SimpleGrantedAuthority("ROLE_USER"));
    }

    @Test
    void getActivity_whenActivityNotFound_returns404() throws Exception {
        when(activityService.findById(activityId, userId)).thenThrow(new ActivityNotFoundException());

        mockMvc.perform(get("/api/activities/1").with(authenticatedAs(userId)))
                .andExpect(status().isNotFound());
    }

    @Test
    void createActivity_whenValidBody_returnsLocationAnd201() throws Exception {
        ActivityRequest activityRequest = new ActivityRequest(
                ActivityType.WEIGHTS,
                Instant.ofEpochSecond(123),
                Duration.ofSeconds(123),
                null,
                "test"
        );
        ActivityResponse activityResponse = new ActivityResponse(
                1L,
                userId,
                ActivityType.WEIGHTS,
                Instant.ofEpochSecond(123),
                Duration.ofSeconds(123),
                null,
                "test",
                Instant.ofEpochSecond(1),
                Instant.ofEpochSecond(1)
        );
        when(activityService.create(activityRequest, userId)).thenReturn(activityResponse);

        mockMvc.perform(post("/api/activities")
                        .with(authenticatedAs(userId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(activityRequest)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/activities/" + activityResponse.id()))
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.activityType").exists());
    }

    @Test
    void updateActivity_whenMissingBodyParameter_returns400() throws Exception {
        String invalidJson = """
                {
                    "startedAt": 123,
                    "duration": 123,
                    "notes": "test"
                }
                """;

        mockMvc.perform(put("/api/activities/1")
                        .with(authenticatedAs(userId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.activityType").exists());
    }

    @Test
    void createActivity_whenNestedDetailsInvalid_returns400WithNestedFieldAndSkipsService() throws Exception {
        String invalidJson = """
                {
                    "activityType": "WEIGHTS",
                    "startedAt": "2026-10-01T08:00:00Z",
                    "details": {
                        "version": "1",
                        "exerciseList": [
                            {
                                "name": "",
                                "workoutSetList": [ { "weight": 10.0, "reps": -1 } ]
                            }
                        ]
                    }
                }
                """;

        mockMvc.perform(post("/api/activities")
                        .with(authenticatedAs(userId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$['details.exerciseList[0].name']").exists())
                .andExpect(jsonPath("$['details.exerciseList[0].workoutSetList[0].reps']").exists());

        verifyNoInteractions(activityService);
    }

    @Test
    void createActivity_withDetails_returnsDetailsInBody() throws Exception {
        ActivityRequest activityRequest = new ActivityRequest(
                ActivityType.WEIGHTS,
                Instant.ofEpochSecond(123),
                null,
                session(),
                null
        );
        ActivityResponse activityResponse = new ActivityResponse(
                1L,
                userId,
                ActivityType.WEIGHTS,
                Instant.ofEpochSecond(123),
                null,
                session(),
                null,
                Instant.ofEpochSecond(1),
                Instant.ofEpochSecond(1)
        );
        when(activityService.create(activityRequest, userId)).thenReturn(activityResponse);

        mockMvc.perform(post("/api/activities")
                        .with(authenticatedAs(userId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(activityRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.duration").doesNotExist())
                .andExpect(jsonPath("$.details.exerciseList[0].name")
                        .value("Vertikaaltõmbed trenažööril pealthaare"))
                .andExpect(jsonPath("$.details.exerciseList[0].workoutSetList[0].weight").value(31.25))
                .andExpect(jsonPath("$.details.exerciseList[0].workoutSetList[2].reps").doesNotExist());
    }

    @Test
    void deleteActivity_whenActivityDeleted_returns204() throws Exception {
        mockMvc.perform(delete("/api/activities/1").with(authenticatedAs(userId)))
                .andExpect(status().isNoContent());
        verify(activityService).deleteById(activityId, userId);
    }

    @Test
    void getActivity_whenNoToken_returns401() throws Exception {
        mockMvc.perform(get("/api/activities/1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getActivity_whenTokenLacksUserRole_returns403() throws Exception {
        mockMvc.perform(get("/api/activities/1")
                        .with(jwt()
                                .jwt(builder -> builder.subject("1"))
                                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void getActivity_passesSubjectFromTokenToService() throws Exception {
        Long otherUserId = 2L;
        ActivityResponse activityResponse = new ActivityResponse(
                1L,
                otherUserId,
                ActivityType.WEIGHTS,
                Instant.ofEpochSecond(123),
                Duration.ofSeconds(123),
                null,
                "test",
                Instant.ofEpochSecond(1),
                Instant.ofEpochSecond(1)
        );
        when(activityService.findById(activityId, otherUserId)).thenReturn(activityResponse);

        mockMvc.perform(get("/api/activities/1").with(authenticatedAs(otherUserId)))
                .andExpect(status().isOk());

        verify(activityService).findById(activityId, otherUserId);
    }
}
