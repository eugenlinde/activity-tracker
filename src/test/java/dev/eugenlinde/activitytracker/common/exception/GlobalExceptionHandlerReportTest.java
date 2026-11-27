package dev.eugenlinde.activitytracker.common.exception;

import dev.eugenlinde.activitytracker.activity.ActivityRepository;
import dev.eugenlinde.activitytracker.activity.controller.ActivityController;
import dev.eugenlinde.activitytracker.activity.service.ActivityService;
import dev.eugenlinde.activitytracker.config.SecurityConfig;
import dev.eugenlinde.activitytracker.errorreport.ErrorReporter;
import dev.eugenlinde.activitytracker.media.MediaRepository;
import dev.eugenlinde.activitytracker.media.service.MediaService;
import dev.eugenlinde.activitytracker.share.ShareRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ActivityController.class)
@Import(SecurityConfig.class)
@ActiveProfiles("test")
class GlobalExceptionHandlerReportTest {

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
    @MockitoBean
    private ErrorReporter errorReporter;

    private static JwtRequestPostProcessor authenticatedAs(Long userId) {
        return jwt()
                .jwt(builder -> builder.subject(String.valueOf(userId)))
                .authorities(new SimpleGrantedAuthority("ROLE_USER"));
    }

    @Test
    void unexpectedException_answers500AndReportsOnce() throws Exception {
        when(activityService.findById(1L, 1L)).thenThrow(new IllegalStateException("boom"));

        mockMvc.perform(get("/api/activities/1?token=abc").with(authenticatedAs(1L)))
                .andExpect(status().isInternalServerError())
                .andExpect(content().string("Internal server error"));

        verify(errorReporter).report(any(IllegalStateException.class), eq("GET"), eq("/api/activities/{id}"));
    }

    @Test
    void expectedException_doesNotReport() throws Exception {
        when(activityService.findById(1L, 1L)).thenThrow(new ActivityNotFoundException());

        mockMvc.perform(get("/api/activities/1").with(authenticatedAs(1L)))
                .andExpect(status().isNotFound());

        verifyNoInteractions(errorReporter);
    }
}
