package dev.eugenlinde.activitytracker.garmin.controller;

import dev.eugenlinde.activitytracker.activity.ActivityRepository;
import dev.eugenlinde.activitytracker.common.exception.GarminKeyExistsException;
import dev.eugenlinde.activitytracker.common.exception.GarminKeyNotFoundException;
import dev.eugenlinde.activitytracker.config.SecurityConfig;
import dev.eugenlinde.activitytracker.garmin.dto.GarminStatusResponse;
import dev.eugenlinde.activitytracker.garmin.model.GarminSyncStatus;
import dev.eugenlinde.activitytracker.garmin.service.GarminConnectionService;
import dev.eugenlinde.activitytracker.media.MediaRepository;
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
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doThrow;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(GarminController.class)
@Import(SecurityConfig.class)
@ActiveProfiles("test")
class GarminControllerTest {
    private static final Long USER_ID = 1L;
    private static final String KEY = "my-secret-garmin-key";
    private static final Instant ADDED = Instant.parse("2026-11-28T09:30:00Z");

    @Autowired
    private MockMvc mockMvc;
    @MockitoBean
    private GarminConnectionService garminConnectionService;
    @MockitoBean
    private ShareRepository shareRepository;
    @MockitoBean
    private MediaRepository mediaRepository;
    @MockitoBean
    private ActivityRepository activityRepository;

    private static JwtRequestPostProcessor authenticatedAs(Long userId) {
        return jwt()
                .jwt(builder -> builder.subject(String.valueOf(userId)))
                .authorities(new SimpleGrantedAuthority("ROLE_USER"));
    }

    private static String body(String key) {
        return "{\"apiKey\":\"" + key + "\"}";
    }

    @Test
    void get_withoutAKey_returnsNotConnected() throws Exception {
        when(garminConnectionService.status(USER_ID)).thenReturn(GarminStatusResponse.notConnected());

        mockMvc.perform(get("/api/garmin").with(authenticatedAs(USER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.connected").value(false))
                .andExpect(jsonPath("$.connectedAt").doesNotExist())
                .andExpect(jsonPath("$.lastSyncAt").doesNotExist())
                .andExpect(jsonPath("$.lastSyncStatus").doesNotExist());
    }

    @Test
    void get_withAKey_returnsStatusWithoutTheKey() throws Exception {
        Instant syncAt = Instant.parse("2026-11-28T10:00:00Z");
        when(garminConnectionService.status(USER_ID))
                .thenReturn(new GarminStatusResponse(true, ADDED, syncAt, GarminSyncStatus.FAILED));

        MvcResult result = mockMvc.perform(get("/api/garmin").with(authenticatedAs(USER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.connected").value(true))
                .andExpect(jsonPath("$.connectedAt").value("2026-11-28T09:30:00Z"))
                .andExpect(jsonPath("$.lastSyncAt").value("2026-11-28T10:00:00Z"))
                .andExpect(jsonPath("$.lastSyncStatus").value("FAILED"))
                .andExpect(jsonPath("$.apiKey").doesNotExist())
                .andReturn();

        assertFalse(result.getResponse().getContentAsString().contains("apiKey"));
    }

    @Test
    void post_whenValid_returns201AndPassesTheKeyForTheTokenSubject() throws Exception {
        when(garminConnectionService.add(USER_ID, KEY))
                .thenReturn(new GarminStatusResponse(true, ADDED, null, null));

        MvcResult result = mockMvc.perform(post("/api/garmin").with(authenticatedAs(USER_ID))
                        .contentType(MediaType.APPLICATION_JSON).content(body(KEY)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.connected").value(true))
                .andExpect(jsonPath("$.lastSyncAt").doesNotExist())
                .andExpect(jsonPath("$.lastSyncStatus").doesNotExist())
                .andExpect(jsonPath("$.apiKey").doesNotExist())
                .andReturn();

        verify(garminConnectionService).add(USER_ID, KEY);
        String responseBody = result.getResponse().getContentAsString();
        assertFalse(responseBody.contains("apiKey"));
        assertFalse(responseBody.contains(KEY));
    }

    @Test
    void post_whenKeyIsBlank_returns400() throws Exception {
        mockMvc.perform(post("/api/garmin").with(authenticatedAs(USER_ID))
                        .contentType(MediaType.APPLICATION_JSON).content(body("   ")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.apiKey[0]").value("Key is required"));

        verifyNoInteractions(garminConnectionService);
    }

    @Test
    void post_whenKeyIsMissing_returns400() throws Exception {
        mockMvc.perform(post("/api/garmin").with(authenticatedAs(USER_ID))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.apiKey[0]").value("Key is required"));

        verifyNoInteractions(garminConnectionService);
    }

    @Test
    void post_whenKeyIsTooLong_returns400WithoutEchoingIt() throws Exception {
        String longKey = "k".repeat(4097);

        mockMvc.perform(post("/api/garmin").with(authenticatedAs(USER_ID))
                        .contentType(MediaType.APPLICATION_JSON).content(body(longKey)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.apiKey[0]").value("Key is too long"))
                .andExpect(content().string(not(containsString(longKey))));

        verifyNoInteractions(garminConnectionService);
    }

    @Test
    void post_whenKeyAtTheLimit_isAccepted() throws Exception {
        String limitKey = "k".repeat(4096);
        when(garminConnectionService.add(USER_ID, limitKey))
                .thenReturn(new GarminStatusResponse(true, ADDED, null, null));

        mockMvc.perform(post("/api/garmin").with(authenticatedAs(USER_ID))
                        .contentType(MediaType.APPLICATION_JSON).content(body(limitKey)))
                .andExpect(status().isCreated());
    }

    @Test
    void post_whenAKeyExists_returns409WithMessage() throws Exception {
        when(garminConnectionService.add(USER_ID, KEY)).thenThrow(new GarminKeyExistsException());

        mockMvc.perform(post("/api/garmin").with(authenticatedAs(USER_ID))
                        .contentType(MediaType.APPLICATION_JSON).content(body(KEY)))
                .andExpect(status().isConflict())
                .andExpect(content().string("A Garmin key is already added. Remove it first."))
                .andExpect(content().string(not(containsString(KEY))));
    }

    @Test
    void delete_whenAKeyExists_returns204() throws Exception {
        mockMvc.perform(delete("/api/garmin").with(authenticatedAs(USER_ID)))
                .andExpect(status().isNoContent());

        verify(garminConnectionService).remove(USER_ID);
    }

    @Test
    void delete_withoutAKey_returns404WithMessage() throws Exception {
        doThrow(new GarminKeyNotFoundException()).when(garminConnectionService).remove(USER_ID);

        mockMvc.perform(delete("/api/garmin").with(authenticatedAs(USER_ID)))
                .andExpect(status().isNotFound())
                .andExpect(content().string("Garmin key not found"));
    }

    @Test
    void allThreeEndpoints_withoutLogin_return401() throws Exception {
        mockMvc.perform(get("/api/garmin")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/garmin").contentType(MediaType.APPLICATION_JSON).content(body(KEY)))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/api/garmin")).andExpect(status().isUnauthorized());

        verifyNoInteractions(garminConnectionService);
    }
}
