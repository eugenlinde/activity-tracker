package dev.eugenlinde.activitytracker.activity.controller;

import dev.eugenlinde.activitytracker.activity.dto.ActivityRequest;
import dev.eugenlinde.activitytracker.activity.dto.ActivityResponse;
import dev.eugenlinde.activitytracker.activity.model.ActivityType;
import dev.eugenlinde.activitytracker.activity.service.ActivityService;
import dev.eugenlinde.activitytracker.common.exception.ActivityNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.time.Instant;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;


@WebMvcTest(ActivityController.class)
class ActivityControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @MockitoBean
    private ActivityService activityService;
    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void getActivity_whenActivityNotFound_returns404() throws Exception {
        when(activityService.findById(anyLong())).thenThrow(new ActivityNotFoundException());

        mockMvc.perform(get("/api/activities/1")).andExpect(status().isNotFound());
    }

    @Test
    void createActivity_whenValidBody_returnsLocationAnd201() throws Exception {
        ActivityRequest activityRequest = new ActivityRequest(
                ActivityType.WEIGHTS,
                Instant.ofEpochSecond(123),
                Duration.ofSeconds(123),
                "test"
        );
        ActivityResponse activityResponse = new ActivityResponse(
                1L,
                ActivityType.WEIGHTS,
                Instant.ofEpochSecond(123),
                Duration.ofSeconds(123),
                "test",
                Instant.ofEpochSecond(1),
                Instant.ofEpochSecond(1)
        );
        when(activityService.create(activityRequest)).thenReturn(activityResponse);

        mockMvc.perform(post("/api/activities")
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
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.activityType").exists());
    }

    @Test
    void deleteActivity_whenActivityDeleted_returns204() throws Exception {
        mockMvc.perform(delete("/api/activities/1")).andExpect(status().isNoContent());
        verify(activityService).deleteById(1L);
    }
}