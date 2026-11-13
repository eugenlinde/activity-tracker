package dev.eugenlinde.activitytracker.steps.controller;

import dev.eugenlinde.activitytracker.activity.ActivityRepository;
import dev.eugenlinde.activitytracker.common.exception.InvalidEntryDateException;
import dev.eugenlinde.activitytracker.common.exception.StepEntryNotFoundException;
import dev.eugenlinde.activitytracker.config.SecurityConfig;
import dev.eugenlinde.activitytracker.media.MediaRepository;
import dev.eugenlinde.activitytracker.share.ShareRepository;
import dev.eugenlinde.activitytracker.steps.dto.StepsResponse;
import dev.eugenlinde.activitytracker.steps.service.StepsService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(StepsController.class)
@Import(SecurityConfig.class)
@ActiveProfiles("test")
class StepsControllerTest {
    private static final LocalDate DATE = LocalDate.parse("2026-11-13");

    @Autowired
    private MockMvc mockMvc;
    @MockitoBean
    private StepsService stepsService;
    @MockitoBean
    private ShareRepository shareRepository;
    @MockitoBean
    private MediaRepository mediaRepository;
    @MockitoBean
    private ActivityRepository activityRepository;

    private final Long userId = 7L;

    private static JwtRequestPostProcessor authenticatedAs(Long userId) {
        return jwt()
                .jwt(builder -> builder.subject(String.valueOf(userId)))
                .authorities(new SimpleGrantedAuthority("ROLE_USER"));
    }

    @Test
    void getSteps_withoutToken_returns401() throws Exception {
        mockMvc.perform(get("/api/steps")).andExpect(status().isUnauthorized());

        verifyNoInteractions(stepsService);
    }

    @Test
    void putSteps_withoutToken_returns401() throws Exception {
        mockMvc.perform(put("/api/steps/2026-11-13")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"steps\": 9870}"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(stepsService);
    }

    @Test
    void deleteSteps_withoutToken_returns401() throws Exception {
        mockMvc.perform(delete("/api/steps/2026-11-13")).andExpect(status().isUnauthorized());

        verifyNoInteractions(stepsService);
    }

    @Test
    void getSteps_withoutRange_asksTheServiceForTheCallersDefaultRange() throws Exception {
        when(stepsService.findAll(userId, null, null)).thenReturn(List.of(new StepsResponse(DATE, 9870)));

        mockMvc.perform(get("/api/steps").with(authenticatedAs(userId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].date").value("2026-11-13"))
                .andExpect(jsonPath("$[0].steps").value(9870));
    }

    @Test
    void getSteps_withRange_passesTheRangeAndTheCallersId() throws Exception {
        when(stepsService.findAll(userId, LocalDate.parse("2026-10-01"), LocalDate.parse("2026-11-13")))
                .thenReturn(List.of());

        mockMvc.perform(get("/api/steps?from=2026-10-01&to=2026-11-13").with(authenticatedAs(userId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        verify(stepsService).findAll(userId, LocalDate.parse("2026-10-01"), LocalDate.parse("2026-11-13"));
    }

    @Test
    void getSteps_withMalformedDate_returns400() throws Exception {
        mockMvc.perform(get("/api/steps?to=tomorrow").with(authenticatedAs(userId)))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(stepsService);
    }

    @Test
    void putSteps_withValidBody_returnsTheSavedEntry() throws Exception {
        when(stepsService.upsert(userId, DATE, 9870)).thenReturn(new StepsResponse(DATE, 9870));

        mockMvc.perform(put("/api/steps/2026-11-13")
                        .with(authenticatedAs(userId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"steps\": 9870}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.date").value("2026-11-13"))
                .andExpect(jsonPath("$.steps").value(9870));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{}",
            "{\"steps\": null}",
            "{\"steps\": -1}",
            "{\"steps\": 100001}",
            "{\"steps\": 9870.5}",
            "{\"steps\": \"many\"}"
    })
    void putSteps_withInvalidBody_returns400AndSavesNothing(String body) throws Exception {
        mockMvc.perform(put("/api/steps/2026-11-13")
                        .with(authenticatedAs(userId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(stepsService);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 100000})
    void putSteps_atTheEdgesOfTheAllowedRange_isAccepted(int steps) throws Exception {
        when(stepsService.upsert(userId, DATE, steps)).thenReturn(new StepsResponse(DATE, steps));

        mockMvc.perform(put("/api/steps/2026-11-13")
                        .with(authenticatedAs(userId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"steps\": " + steps + "}"))
                .andExpect(status().isOk());
    }

    @Test
    void putSteps_withMalformedDateInPath_returns400() throws Exception {
        mockMvc.perform(put("/api/steps/13-11-2026")
                        .with(authenticatedAs(userId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"steps\": 9870}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(stepsService);
    }

    @Test
    void putSteps_whenServiceRejectsTheDate_returns400() throws Exception {
        when(stepsService.upsert(userId, DATE, 9870)).thenThrow(new InvalidEntryDateException());

        mockMvc.perform(put("/api/steps/2026-11-13")
                        .with(authenticatedAs(userId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"steps\": 9870}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void deleteSteps_whenEntryExists_returns204() throws Exception {
        mockMvc.perform(delete("/api/steps/2026-11-13").with(authenticatedAs(userId)))
                .andExpect(status().isNoContent());

        verify(stepsService).delete(userId, DATE);
    }

    @Test
    void deleteSteps_whenNoEntry_returns404() throws Exception {
        doThrow(new StepEntryNotFoundException()).when(stepsService).delete(userId, DATE);

        mockMvc.perform(delete("/api/steps/2026-11-13").with(authenticatedAs(userId)))
                .andExpect(status().isNotFound());
    }
}
