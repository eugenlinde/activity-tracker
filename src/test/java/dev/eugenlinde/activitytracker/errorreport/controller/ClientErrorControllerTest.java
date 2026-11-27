package dev.eugenlinde.activitytracker.errorreport.controller;

import dev.eugenlinde.activitytracker.activity.ActivityRepository;
import dev.eugenlinde.activitytracker.config.SecurityConfig;
import dev.eugenlinde.activitytracker.errorreport.ErrorReporter;
import dev.eugenlinde.activitytracker.media.MediaRepository;
import dev.eugenlinde.activitytracker.share.ShareRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ClientErrorController.class)
@Import(SecurityConfig.class)
@ActiveProfiles("test")
@ExtendWith(OutputCaptureExtension.class)
class ClientErrorControllerTest {
    private static final String URL = "/api/client-errors";
    private static final String FRAME = "/assets/index-AbC123.js:10:1234";

    @Autowired
    private MockMvc mockMvc;
    @MockitoBean
    private ErrorReporter errorReporter;
    @MockitoBean
    private ShareRepository shareRepository;
    @MockitoBean
    private MediaRepository mediaRepository;
    @MockitoBean
    private ActivityRepository activityRepository;

    private static JwtRequestPostProcessor user() {
        return jwt()
                .jwt(builder -> builder.subject("7"))
                .authorities(new SimpleGrantedAuthority("ROLE_USER"));
    }

    private static String json(String name, String frames, String route) {
        StringBuilder text = new StringBuilder("{");
        boolean first = true;
        if (name != null) {
            text.append("\"name\":").append(name);
            first = false;
        }
        if (frames != null) {
            text.append(first ? "" : ",").append("\"frames\":").append(frames);
            first = false;
        }
        if (route != null) {
            text.append(first ? "" : ",").append("\"route\":").append(route);
        }
        return text.append('}').toString();
    }

    private static String quoted(String value) {
        return "\"" + value + "\"";
    }

    private static String frameList(String... frames) {
        StringBuilder text = new StringBuilder("[");
        for (int i = 0; i < frames.length; i++) {
            text.append(i == 0 ? "" : ",").append(frames[i] == null ? "null" : quoted(frames[i]));
        }
        return text.append(']').toString();
    }

    private void expectBadRequest(String body) throws Exception {
        mockMvc.perform(post(URL).with(user()).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(errorReporter);
    }

    @Test
    void goodReport_returns204AndReachesTheReporterOnce() throws Exception {
        mockMvc.perform(post(URL).with(user()).contentType(MediaType.APPLICATION_JSON)
                        .content(json(quoted("TypeError"), frameList(FRAME), quoted("/shared/:ownerId"))))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        verify(errorReporter).reportBrowser("TypeError", List.of(FRAME), "/shared/:ownerId");
        verifyNoMoreInteractions(errorReporter);
    }

    @Test
    void limitsExactlyAtTheEdge_areAccepted() throws Exception {
        String[] frames = new String[20];
        java.util.Arrays.fill(frames, FRAME);
        String name = "a".repeat(60);
        String route = "/".repeat(100);

        mockMvc.perform(post(URL).with(user()).contentType(MediaType.APPLICATION_JSON)
                        .content(json(quoted(name), frameList(frames), quoted(route))))
                .andExpect(status().isNoContent());

        verify(errorReporter).reportBrowser(name, List.of(frames), route);
    }

    @Test
    void nameMissing_returns400() throws Exception {
        expectBadRequest(json(null, frameList(FRAME), quoted("/")));
    }

    @Test
    void nameOf61Letters_returns400() throws Exception {
        expectBadRequest(json(quoted("a".repeat(61)), frameList(FRAME), quoted("/")));
    }

    @Test
    void nameWithDigit_returns400() throws Exception {
        expectBadRequest(json(quoted("Error2"), frameList(FRAME), quoted("/")));
    }

    @Test
    void nameWithSpace_returns400() throws Exception {
        expectBadRequest(json(quoted("My Error"), frameList(FRAME), quoted("/")));
    }

    @Test
    void framesMissing_returns400() throws Exception {
        expectBadRequest(json(quoted("Error"), null, quoted("/")));
    }

    @Test
    void framesEmpty_returns400() throws Exception {
        expectBadRequest(json(quoted("Error"), "[]", quoted("/")));
    }

    @Test
    void twentyOneFrames_returns400() throws Exception {
        String[] frames = new String[21];
        java.util.Arrays.fill(frames, FRAME);
        expectBadRequest(json(quoted("Error"), frameList(frames), quoted("/")));
    }

    @Test
    void frameOf201Characters_returns400() throws Exception {
        String frame = "a".repeat(201 - ":1:1".length()) + ":1:1";
        expectBadRequest(json(quoted("Error"), frameList(frame), quoted("/")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/assets/a.js", "/assets/a.js:10", "/assets/a b.js:1:2", "/assets/a.js?v=1:1:2", "/assets/a.js:x:2"})
    void badFrame_returns400(String frame) throws Exception {
        expectBadRequest(json(quoted("Error"), frameList(frame), quoted("/")));
    }

    @Test
    void nullFrame_returns400() throws Exception {
        expectBadRequest(json(quoted("Error"), frameList(FRAME, null), quoted("/")));
    }

    @Test
    void routeMissing_returns400() throws Exception {
        expectBadRequest(json(quoted("Error"), frameList(FRAME), null));
    }

    @Test
    void routeOf101Characters_returns400() throws Exception {
        expectBadRequest(json(quoted("Error"), frameList(FRAME), quoted("/".repeat(101))));
    }

    @Test
    void routeWithDigit_returns400() throws Exception {
        expectBadRequest(json(quoted("Error"), frameList(FRAME), quoted("/shared/42")));
    }

    @Test
    void routeWithQuestionMark_returns400() throws Exception {
        expectBadRequest(json(quoted("Error"), frameList(FRAME), quoted("/shared?x")));
    }

    @Test
    void bodyThatIsNotJson_returns400() throws Exception {
        expectBadRequest("this is not json");
    }

    @Test
    void withoutToken_returns401() throws Exception {
        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON)
                        .content(json(quoted("Error"), frameList(FRAME), quoted("/"))))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(errorReporter);
    }

    @Test
    void rejectedValue_isNeverLogged(CapturedOutput output) throws Exception {
        expectBadRequest(json(quoted("Error"), frameList("zebrafinch secret"), quoted("/")));
        expectBadRequest(json(quoted("zebrafinch 7"), frameList(FRAME), quoted("/")));

        assertFalse(output.getAll().contains("zebrafinch"));
    }
}
