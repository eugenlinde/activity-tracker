package dev.eugenlinde.activitytracker.share.controller;

import dev.eugenlinde.activitytracker.activity.ActivityRepository;
import dev.eugenlinde.activitytracker.common.exception.SelfShareException;
import dev.eugenlinde.activitytracker.common.exception.ShareExistsException;
import dev.eugenlinde.activitytracker.common.exception.ShareNotFoundException;
import dev.eugenlinde.activitytracker.common.exception.UserNotFoundException;
import dev.eugenlinde.activitytracker.config.SecurityConfig;
import dev.eugenlinde.activitytracker.media.MediaRepository;
import dev.eugenlinde.activitytracker.share.ShareRepository;
import dev.eugenlinde.activitytracker.share.dto.ShareResponse;
import dev.eugenlinde.activitytracker.share.service.ShareService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ShareController.class)
@Import(SecurityConfig.class)
@ActiveProfiles("test")
class ShareControllerTest {
    private final Long userId = 1L;
    private final String validBody = "{\"email\":\"viewer@example.com\"}";

    @Autowired
    private MockMvc mockMvc;
    @MockitoBean
    private ShareService shareService;
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

    @Test
    void createShare_whenValid_returns201AndPassesTokenSubject() throws Exception {
        mockMvc.perform(post("/api/shares").with(authenticatedAs(userId))
                        .contentType(MediaType.APPLICATION_JSON).content(validBody))
                .andExpect(status().isCreated())
                .andExpect(content().string(""));

        verify(shareService).create(userId, "viewer@example.com");
    }

    @Test
    void createShare_whenEmailInvalid_returns400AndSkipsService() throws Exception {
        mockMvc.perform(post("/api/shares").with(authenticatedAs(userId))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"not-an-email\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.email").exists());

        verifyNoInteractions(shareService);
    }

    @Test
    void createShare_whenViewerUnknown_returns404() throws Exception {
        doThrow(new UserNotFoundException()).when(shareService).create(userId, "viewer@example.com");

        mockMvc.perform(post("/api/shares").with(authenticatedAs(userId))
                        .contentType(MediaType.APPLICATION_JSON).content(validBody))
                .andExpect(status().isNotFound());
    }

    @Test
    void createShare_whenSelfShare_returns400() throws Exception {
        doThrow(new SelfShareException()).when(shareService).create(userId, "viewer@example.com");

        mockMvc.perform(post("/api/shares").with(authenticatedAs(userId))
                        .contentType(MediaType.APPLICATION_JSON).content(validBody))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createShare_whenDuplicate_returns409() throws Exception {
        doThrow(new ShareExistsException()).when(shareService).create(userId, "viewer@example.com");

        mockMvc.perform(post("/api/shares").with(authenticatedAs(userId))
                        .contentType(MediaType.APPLICATION_JSON).content(validBody))
                .andExpect(status().isConflict())
                .andExpect(content().string("Share already exists"));
    }

    @Test
    void createShare_whenConstraintViolated_returns409WithoutLeakingSql() throws Exception {
        doThrow(new DataIntegrityViolationException("duplicate key value violates unique constraint \"uq_shares_active_pair\""))
                .when(shareService).create(userId, "viewer@example.com");

        mockMvc.perform(post("/api/shares").with(authenticatedAs(userId))
                        .contentType(MediaType.APPLICATION_JSON).content(validBody))
                .andExpect(status().isConflict())
                .andExpect(content().string(not(containsString("uq_shares"))));
    }

    @Test
    void createShare_withoutToken_returns401() throws Exception {
        mockMvc.perform(post("/api/shares").contentType(MediaType.APPLICATION_JSON).content(validBody))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(shareService);
    }

    @Test
    void getShares_returnsCallersShares() throws Exception {
        when(shareService.getUserShares(userId)).thenReturn(List.of(new ShareResponse(10L, userId, "viewer@example.com")));

        mockMvc.perform(get("/api/shares").with(authenticatedAs(userId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(10))
                .andExpect(jsonPath("$[0].email").value("viewer@example.com"));
    }

    @Test
    void getReceivedShares_returnsSharesForCaller() throws Exception {
        when(shareService.getUserReceivedShares(userId)).thenReturn(List.of(new ShareResponse(10L, 5L, "owner@example.com")));

        mockMvc.perform(get("/api/shares/received").with(authenticatedAs(userId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].ownerId").value(5));
    }

    @Test
    void deleteShare_whenOwned_returns204() throws Exception {
        mockMvc.perform(delete("/api/shares/10").with(authenticatedAs(userId)))
                .andExpect(status().isNoContent());

        verify(shareService).deleteById(10L, userId);
    }

    @Test
    void deleteShare_whenNotOwned_returns404() throws Exception {
        doThrow(new ShareNotFoundException()).when(shareService).deleteById(10L, userId);

        mockMvc.perform(delete("/api/shares/10").with(authenticatedAs(userId)))
                .andExpect(status().isNotFound());
    }
}
