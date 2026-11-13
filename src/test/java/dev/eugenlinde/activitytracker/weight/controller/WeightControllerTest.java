package dev.eugenlinde.activitytracker.weight.controller;

import dev.eugenlinde.activitytracker.activity.ActivityRepository;
import dev.eugenlinde.activitytracker.common.exception.InvalidEntryDateException;
import dev.eugenlinde.activitytracker.common.exception.WeightEntryNotFoundException;
import dev.eugenlinde.activitytracker.config.SecurityConfig;
import dev.eugenlinde.activitytracker.media.MediaRepository;
import dev.eugenlinde.activitytracker.share.ShareRepository;
import dev.eugenlinde.activitytracker.weight.dto.WeightResponse;
import dev.eugenlinde.activitytracker.weight.service.WeightService;
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

import java.math.BigDecimal;
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

@WebMvcTest(WeightController.class)
@Import(SecurityConfig.class)
@ActiveProfiles("test")
class WeightControllerTest {
    private static final LocalDate DATE = LocalDate.parse("2026-11-13");

    @Autowired
    private MockMvc mockMvc;
    @MockitoBean
    private WeightService weightService;
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
    void getWeights_withoutToken_returns401() throws Exception {
        mockMvc.perform(get("/api/weights")).andExpect(status().isUnauthorized());

        verifyNoInteractions(weightService);
    }

    @Test
    void putWeight_withoutToken_returns401() throws Exception {
        mockMvc.perform(put("/api/weights/2026-11-13")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"weightKg\": 81.3}"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(weightService);
    }

    @Test
    void deleteWeight_withoutToken_returns401() throws Exception {
        mockMvc.perform(delete("/api/weights/2026-11-13")).andExpect(status().isUnauthorized());

        verifyNoInteractions(weightService);
    }

    @Test
    void getWeights_withoutRange_asksTheServiceForTheCallersDefaultRange() throws Exception {
        when(weightService.findAll(userId, null, null))
                .thenReturn(List.of(new WeightResponse(DATE, new BigDecimal("81.3"))));

        mockMvc.perform(get("/api/weights").with(authenticatedAs(userId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].date").value("2026-11-13"))
                .andExpect(jsonPath("$[0].weightKg").value(81.3));
    }

    @Test
    void getWeights_withRange_passesTheRangeAndTheCallersId() throws Exception {
        when(weightService.findAll(userId, LocalDate.parse("2026-08-01"), LocalDate.parse("2026-11-13")))
                .thenReturn(List.of());

        mockMvc.perform(get("/api/weights?from=2026-08-01&to=2026-11-13").with(authenticatedAs(userId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        verify(weightService).findAll(userId, LocalDate.parse("2026-08-01"), LocalDate.parse("2026-11-13"));
    }

    @Test
    void getWeights_withMalformedDate_returns400() throws Exception {
        mockMvc.perform(get("/api/weights?from=yesterday").with(authenticatedAs(userId)))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(weightService);
    }

    @Test
    void putWeight_withValidBody_returnsTheSavedEntry() throws Exception {
        when(weightService.upsert(userId, DATE, new BigDecimal("81.3")))
                .thenReturn(new WeightResponse(DATE, new BigDecimal("81.3")));

        mockMvc.perform(put("/api/weights/2026-11-13")
                        .with(authenticatedAs(userId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"weightKg\": 81.3}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.date").value("2026-11-13"))
                .andExpect(jsonPath("$.weightKg").value(81.3));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{}",
            "{\"weightKg\": null}",
            "{\"weightKg\": 19.9}",
            "{\"weightKg\": 400.1}",
            "{\"weightKg\": 81.25}",
            "{\"weightKg\": -5}",
            "{\"weightKg\": \"heavy\"}"
    })
    void putWeight_withInvalidBody_returns400AndSavesNothing(String body) throws Exception {
        mockMvc.perform(put("/api/weights/2026-11-13")
                        .with(authenticatedAs(userId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(weightService);
    }

    @ParameterizedTest
    @ValueSource(strings = {"20.0", "400.0", "81"})
    void putWeight_atTheEdgesOfTheAllowedRange_isAccepted(String weightKg) throws Exception {
        when(weightService.upsert(userId, DATE, new BigDecimal(weightKg)))
                .thenReturn(new WeightResponse(DATE, new BigDecimal(weightKg)));

        mockMvc.perform(put("/api/weights/2026-11-13")
                        .with(authenticatedAs(userId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"weightKg\": " + weightKg + "}"))
                .andExpect(status().isOk());
    }

    @Test
    void putWeight_withMalformedDateInPath_returns400() throws Exception {
        mockMvc.perform(put("/api/weights/13-11-2026")
                        .with(authenticatedAs(userId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"weightKg\": 81.3}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(weightService);
    }

    @Test
    void putWeight_whenServiceRejectsTheDate_returns400() throws Exception {
        when(weightService.upsert(userId, DATE, new BigDecimal("81.3"))).thenThrow(new InvalidEntryDateException());

        mockMvc.perform(put("/api/weights/2026-11-13")
                        .with(authenticatedAs(userId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"weightKg\": 81.3}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void deleteWeight_whenEntryExists_returns204() throws Exception {
        mockMvc.perform(delete("/api/weights/2026-11-13").with(authenticatedAs(userId)))
                .andExpect(status().isNoContent());

        verify(weightService).delete(userId, DATE);
    }

    @Test
    void deleteWeight_whenNoEntry_returns404() throws Exception {
        doThrow(new WeightEntryNotFoundException()).when(weightService).delete(userId, DATE);

        mockMvc.perform(delete("/api/weights/2026-11-13").with(authenticatedAs(userId)))
                .andExpect(status().isNotFound());
    }
}
