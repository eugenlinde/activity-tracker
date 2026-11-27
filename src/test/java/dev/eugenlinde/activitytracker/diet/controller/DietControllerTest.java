package dev.eugenlinde.activitytracker.diet.controller;

import dev.eugenlinde.activitytracker.activity.ActivityRepository;
import dev.eugenlinde.activitytracker.common.exception.DietEntryNotFoundException;
import dev.eugenlinde.activitytracker.common.exception.InvalidDateRangeException;
import dev.eugenlinde.activitytracker.common.exception.InvalidEntryDateException;
import dev.eugenlinde.activitytracker.config.SecurityConfig;
import dev.eugenlinde.activitytracker.diet.dto.DietResponse;
import dev.eugenlinde.activitytracker.diet.model.DietStatus;
import dev.eugenlinde.activitytracker.diet.service.DietService;
import dev.eugenlinde.activitytracker.media.MediaRepository;
import dev.eugenlinde.activitytracker.share.ShareRepository;
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

@WebMvcTest(DietController.class)
@Import(SecurityConfig.class)
@ActiveProfiles("test")
class DietControllerTest {
    private static final LocalDate DATE = LocalDate.parse("2026-11-27");

    @Autowired
    private MockMvc mockMvc;
    @MockitoBean
    private DietService dietService;
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
    void getDiet_withoutToken_returns401() throws Exception {
        mockMvc.perform(get("/api/diet?from=2026-11-01&to=2026-11-30")).andExpect(status().isUnauthorized());

        verifyNoInteractions(dietService);
    }

    @Test
    void putDiet_withoutToken_returns401() throws Exception {
        mockMvc.perform(put("/api/diet/2026-11-27")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\": \"MORE\"}"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(dietService);
    }

    @Test
    void deleteDiet_withoutToken_returns401() throws Exception {
        mockMvc.perform(delete("/api/diet/2026-11-27")).andExpect(status().isUnauthorized());

        verifyNoInteractions(dietService);
    }

    @Test
    void getDiet_passesTheRangeAndTheCallersIdAndReturnsTheEntries() throws Exception {
        when(dietService.findAll(userId, LocalDate.parse("2026-11-01"), LocalDate.parse("2026-11-30")))
                .thenReturn(List.of(
                        new DietResponse(DATE, DietStatus.MORE, "Pizza night"),
                        new DietResponse(LocalDate.parse("2026-11-28"), DietStatus.ON_PLAN, null)));

        mockMvc.perform(get("/api/diet?from=2026-11-01&to=2026-11-30").with(authenticatedAs(userId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].date").value("2026-11-27"))
                .andExpect(jsonPath("$[0].status").value("MORE"))
                .andExpect(jsonPath("$[0].comment").value("Pizza night"))
                .andExpect(jsonPath("$[1].status").value("ON_PLAN"))
                .andExpect(jsonPath("$[1].comment").value((Object) null));

        verify(dietService).findAll(userId, LocalDate.parse("2026-11-01"), LocalDate.parse("2026-11-30"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "/api/diet",
            "/api/diet?from=2026-11-01",
            "/api/diet?to=2026-11-30",
            "/api/diet?from=yesterday&to=2026-11-30",
            "/api/diet?from=2026-11-01&to=30-11-2026"
    })
    void getDiet_withMissingOrMalformedParameters_returns400(String url) throws Exception {
        mockMvc.perform(get(url).with(authenticatedAs(userId)))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(dietService);
    }

    @Test
    void getDiet_whenServiceRejectsTheRange_returns400() throws Exception {
        when(dietService.findAll(userId, LocalDate.parse("2026-11-30"), LocalDate.parse("2026-11-01")))
                .thenThrow(new InvalidDateRangeException("'from' must not be after 'to'"));

        mockMvc.perform(get("/api/diet?from=2026-11-30&to=2026-11-01").with(authenticatedAs(userId)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void putDiet_withValidBody_returnsTheSavedEntry() throws Exception {
        when(dietService.upsert(userId, DATE, DietStatus.MORE, "Pizza night"))
                .thenReturn(new DietResponse(DATE, DietStatus.MORE, "Pizza night"));

        mockMvc.perform(put("/api/diet/2026-11-27")
                        .with(authenticatedAs(userId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\": \"MORE\", \"comment\": \"Pizza night\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.date").value("2026-11-27"))
                .andExpect(jsonPath("$.status").value("MORE"))
                .andExpect(jsonPath("$.comment").value("Pizza night"));
    }

    @Test
    void putDiet_withoutComment_passesNullComment() throws Exception {
        when(dietService.upsert(userId, DATE, DietStatus.LESS, null))
                .thenReturn(new DietResponse(DATE, DietStatus.LESS, null));

        mockMvc.perform(put("/api/diet/2026-11-27")
                        .with(authenticatedAs(userId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\": \"LESS\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.comment").value((Object) null));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{}",
            "{\"status\": null}",
            "{\"status\": \"HUNGRY\"}",
            "{\"status\": \"on_plan\"}",
            "{\"status\": \"MORE\", \"comment\": \"__LONG501__\"}"
    })
    void putDiet_withInvalidBody_returns400AndSavesNothing(String body) throws Exception {
        mockMvc.perform(put("/api/diet/2026-11-27")
                        .with(authenticatedAs(userId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body.replace("__LONG501__", "a".repeat(501))))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(dietService);
    }

    @Test
    void putDiet_withA500CharacterComment_isAccepted() throws Exception {
        String comment = "a".repeat(500);
        when(dietService.upsert(userId, DATE, DietStatus.MORE, comment))
                .thenReturn(new DietResponse(DATE, DietStatus.MORE, comment));

        mockMvc.perform(put("/api/diet/2026-11-27")
                        .with(authenticatedAs(userId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\": \"MORE\", \"comment\": \"" + comment + "\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void putDiet_withMalformedDateInPath_returns400() throws Exception {
        mockMvc.perform(put("/api/diet/27-11-2026")
                        .with(authenticatedAs(userId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\": \"MORE\"}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(dietService);
    }

    @Test
    void putDiet_whenServiceRejectsTheDate_returns400() throws Exception {
        when(dietService.upsert(userId, DATE, DietStatus.MORE, null)).thenThrow(new InvalidEntryDateException());

        mockMvc.perform(put("/api/diet/2026-11-27")
                        .with(authenticatedAs(userId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\": \"MORE\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void deleteDiet_whenEntryExists_returns204() throws Exception {
        mockMvc.perform(delete("/api/diet/2026-11-27").with(authenticatedAs(userId)))
                .andExpect(status().isNoContent());

        verify(dietService).delete(userId, DATE);
    }

    @Test
    void deleteDiet_whenNoEntry_returns404() throws Exception {
        doThrow(new DietEntryNotFoundException()).when(dietService).delete(userId, DATE);

        mockMvc.perform(delete("/api/diet/2026-11-27").with(authenticatedAs(userId)))
                .andExpect(status().isNotFound());
    }
}
