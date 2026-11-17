package dev.eugenlinde.activitytracker.importer.controller;

import dev.eugenlinde.activitytracker.activity.ActivityRepository;
import dev.eugenlinde.activitytracker.common.exception.EmptyImportFileException;
import dev.eugenlinde.activitytracker.common.exception.ImportLimitExceededException;
import dev.eugenlinde.activitytracker.common.exception.ImportMappingException;
import dev.eugenlinde.activitytracker.common.exception.ImportTooLargeException;
import dev.eugenlinde.activitytracker.common.exception.InvalidEntryDateException;
import dev.eugenlinde.activitytracker.common.exception.UnsupportedMediaTypeException;
import dev.eugenlinde.activitytracker.config.SecurityConfig;
import dev.eugenlinde.activitytracker.importer.dto.ImportConfirmRequest;
import dev.eugenlinde.activitytracker.importer.dto.ImportConfirmResponse;
import dev.eugenlinde.activitytracker.importer.dto.ImportPreviewResponse;
import dev.eugenlinde.activitytracker.importer.dto.StepsPreview;
import dev.eugenlinde.activitytracker.importer.dto.WeightPreview;
import dev.eugenlinde.activitytracker.importer.service.ImportConfirmService;
import dev.eugenlinde.activitytracker.importer.service.ImportPreviewService;
import dev.eugenlinde.activitytracker.media.MediaRepository;
import dev.eugenlinde.activitytracker.share.ShareRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ImportController.class)
@Import(SecurityConfig.class)
@ActiveProfiles("test")
class ImportControllerTest {

    private static final String VALID_CONFIRM = """
            {
              "workouts": [{
                "startedAt": "2026-11-03T10:00:00Z",
                "notes": "Nädal 1 / Treening 1",
                "details": {"version": "1", "exerciseList": [
                  {"name": "Kükk", "targetReps": "5-8", "repsInReserve": null, "comment": null, "videoUrl": null,
                   "workoutSetList": [{"weight": 100, "reps": 8}]}
                ]}
              }],
              "weights": [{"date": "2026-11-01", "weightKg": 81.3}],
              "steps": [{"date": "2026-11-01", "steps": 10234}]
            }
            """;

    @Autowired
    private MockMvc mockMvc;
    @MockitoBean
    private ImportPreviewService importPreviewService;
    @MockitoBean
    private ImportConfirmService importConfirmService;
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

    private static MockMultipartFile csv(String name, String text) {
        return new MockMultipartFile("file", name, "text/csv", text.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void preview_withoutToken_returns401() throws Exception {
        mockMvc.perform(multipart("/api/imports/preview").file(csv("kaal.csv", "a,b\n")))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(importPreviewService);
    }

    @Test
    void confirm_withoutToken_returns401() throws Exception {
        mockMvc.perform(post("/api/imports/confirm").contentType(MediaType.APPLICATION_JSON).content(VALID_CONFIRM))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(importConfirmService);
    }

    @Test
    void preview_passesTheCallerTheFileNameAndBytesAndReturnsThePreview() throws Exception {
        when(importPreviewService.preview(eq(userId), eq("kaal.csv"), any())).thenReturn(new ImportPreviewResponse(
                "TABLE",
                List.of(),
                List.of(new WeightPreview(LocalDate.parse("2026-11-02"), new BigDecimal("81.1"), new BigDecimal("82.0"))),
                List.of(new StepsPreview(LocalDate.parse("2026-11-02"), 9876, null)),
                1, 0, 2
        ));

        mockMvc.perform(multipart("/api/imports/preview").file(csv("kaal.csv", "Kuupäev;Kaal\n")).with(authenticatedAs(userId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.kind").value("TABLE"))
                .andExpect(jsonPath("$.weights[0].date").value("2026-11-02"))
                .andExpect(jsonPath("$.weights[0].weightKg").value(81.1))
                .andExpect(jsonPath("$.weights[0].existingWeightKg").value(82.0))
                .andExpect(jsonPath("$.steps[0].steps").value(9876))
                .andExpect(jsonPath("$.steps[0].existingSteps").doesNotExist())
                .andExpect(jsonPath("$.unchangedWeights").value(1))
                .andExpect(jsonPath("$.unreadableRows").value(2));

        ArgumentCaptor<byte[]> bytes = ArgumentCaptor.forClass(byte[].class);
        verify(importPreviewService).preview(eq(userId), eq("kaal.csv"), bytes.capture());
        assertEquals("Kuupäev;Kaal\n", new String(bytes.getValue(), StandardCharsets.UTF_8));
    }

    @Test
    void preview_withoutAFilePart_returns400() throws Exception {
        mockMvc.perform(multipart("/api/imports/preview").with(authenticatedAs(userId)))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(importPreviewService);
    }

    @Test
    void preview_notACsv_returns415() throws Exception {
        when(importPreviewService.preview(any(), any(), any())).thenThrow(new UnsupportedMediaTypeException("Only .csv"));

        mockMvc.perform(multipart("/api/imports/preview").file(csv("plaan.xlsx", "x")).with(authenticatedAs(userId)))
                .andExpect(status().isUnsupportedMediaType());
    }

    @Test
    void preview_emptyFile_returns400() throws Exception {
        when(importPreviewService.preview(any(), any(), any())).thenThrow(new EmptyImportFileException("The file is empty."));

        mockMvc.perform(multipart("/api/imports/preview").file(csv("x.csv", "")).with(authenticatedAs(userId)))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("The file is empty."));
    }

    @Test
    void preview_tooLarge_returns413() throws Exception {
        when(importPreviewService.preview(any(), any(), any())).thenThrow(new ImportTooLargeException("too large"));

        mockMvc.perform(multipart("/api/imports/preview").file(csv("x.csv", "a")).with(authenticatedAs(userId)))
                .andExpect(status().is(413))
                .andExpect(content().string("too large"));
    }

    @Test
    void preview_overTheDailyLimit_returns429() throws Exception {
        when(importPreviewService.preview(any(), any(), any())).thenThrow(new ImportLimitExceededException("limit"));

        mockMvc.perform(multipart("/api/imports/preview").file(csv("x.csv", "a")).with(authenticatedAs(userId)))
                .andExpect(status().isTooManyRequests())
                .andExpect(content().string("limit"));
    }

    @Test
    void preview_modelFailure_returns502WithoutInternalDetail() throws Exception {
        when(importPreviewService.preview(any(), any(), any()))
                .thenThrow(new ImportMappingException("drafts[0].exercises[0].reps: must not be empty"));

        mockMvc.perform(multipart("/api/imports/preview").file(csv("x.csv", "a")).with(authenticatedAs(userId)))
                .andExpect(status().isBadGateway())
                .andExpect(content().string(not(containsString("drafts"))));
    }

    @Test
    void confirm_valid_passesTheParsedRequestForTheCallerAndReturnsCounts() throws Exception {
        when(importConfirmService.confirm(eq(userId), any())).thenReturn(new ImportConfirmResponse(1, 1, 1));

        mockMvc.perform(post("/api/imports/confirm")
                        .with(authenticatedAs(userId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_CONFIRM))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.workouts").value(1))
                .andExpect(jsonPath("$.weights").value(1))
                .andExpect(jsonPath("$.steps").value(1));

        ArgumentCaptor<ImportConfirmRequest> request = ArgumentCaptor.forClass(ImportConfirmRequest.class);
        verify(importConfirmService).confirm(eq(userId), request.capture());
        assertEquals(Instant.parse("2026-11-03T10:00:00Z"), request.getValue().workouts().getFirst().startedAt());
        assertEquals("Kükk", request.getValue().workouts().getFirst().details().exerciseList().getFirst().name());
        assertEquals(new BigDecimal("81.3"), request.getValue().weights().getFirst().weightKg());
        assertEquals(10234, request.getValue().steps().getFirst().steps());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"workouts\":[{\"startedAt\":\"2999-01-01T10:00:00Z\",\"details\":{\"version\":\"1\",\"exerciseList\":[]}}],\"weights\":[],\"steps\":[]}",
            "{\"workouts\":[{\"details\":{\"version\":\"1\",\"exerciseList\":[]}}],\"weights\":[],\"steps\":[]}",
            "{\"workouts\":[{\"startedAt\":\"2026-11-03T10:00:00Z\"}],\"weights\":[],\"steps\":[]}",
            "{\"workouts\":[{\"startedAt\":\"2026-11-03T10:00:00Z\",\"details\":{\"version\":\"1\",\"exerciseList\":[{\"name\":\" \",\"workoutSetList\":[]}]}}],\"weights\":[],\"steps\":[]}",
            "{\"workouts\":[{\"startedAt\":\"2026-11-03T10:00:00Z\",\"details\":{\"version\":\"1\",\"exerciseList\":[{\"name\":\"Kükk\",\"workoutSetList\":[{\"weight\":-1,\"reps\":5}]}]}}],\"weights\":[],\"steps\":[]}",
            "{\"workouts\":[],\"weights\":[{\"date\":\"2026-11-01\",\"weightKg\":500}],\"steps\":[]}",
            "{\"workouts\":[],\"weights\":[{\"date\":\"2026-11-01\",\"weightKg\":81.34}],\"steps\":[]}",
            "{\"workouts\":[],\"weights\":[{\"weightKg\":81.3}],\"steps\":[]}",
            "{\"workouts\":[],\"weights\":[],\"steps\":[{\"date\":\"2026-11-01\",\"steps\":-1}]}",
            "{\"workouts\":[],\"weights\":[],\"steps\":[{\"date\":\"2026-11-01\",\"steps\":100001}]}",
            "{\"workouts\":[],\"weights\":[],\"steps\":[{\"date\":\"2026-11-01\"}]}",
            "{\"weights\":[],\"steps\":[]}",
            "{\"workouts\":[],\"steps\":[]}",
            "{\"workouts\":[],\"weights\":[]}"
    })
    void confirm_invalidBody_returns400AndWritesNothing(String body) throws Exception {
        mockMvc.perform(post("/api/imports/confirm")
                        .with(authenticatedAs(userId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(importConfirmService);
    }

    @Test
    void confirm_dateRefusedByTheService_returns400() throws Exception {
        when(importConfirmService.confirm(eq(userId), any())).thenThrow(new InvalidEntryDateException());

        mockMvc.perform(post("/api/imports/confirm")
                        .with(authenticatedAs(userId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_CONFIRM))
                .andExpect(status().isBadRequest());
    }
}
