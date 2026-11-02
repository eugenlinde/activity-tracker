package dev.eugenlinde.activitytracker.authentication;

import dev.eugenlinde.activitytracker.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@Transactional
class AuthenticationFlowTest {
    private static final String EMAIL = "auth-flow@example.com";
    private static final String PASSWORD = "correct-horse";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void signup_returns201_thenDuplicateReturns409() throws Exception {
        signUp().andExpect(status().isCreated());

        signUp().andExpect(status().isConflict());
    }

    @Test
    void signup_whenBodyInvalid_returns400PerField() throws Exception {
        mockMvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"nope\",\"password\":\"short\",\"firstName\":\"A\",\"lastName\":\"B\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.email").exists())
                .andExpect(jsonPath("$.password").exists())
                .andExpect(jsonPath("$.firstName").exists());
    }

    @Test
    void login_withCorrectCredentials_returnsTokenThatAuthenticatesApiRequests() throws Exception {
        signUp().andExpect(status().isCreated());

        String token = logIn(EMAIL, PASSWORD);

        mockMvc.perform(get("/api/activities").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void login_withWrongPassword_returns401() throws Exception {
        signUp().andExpect(status().isCreated());

        mockMvc.perform(loginRequest(EMAIL, "wrong-password"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void login_withUnknownEmail_returnsSame401AsWrongPassword() throws Exception {
        signUp().andExpect(status().isCreated());
        String wrongPasswordBody = mockMvc.perform(loginRequest(EMAIL, "wrong-password"))
                .andReturn().getResponse().getContentAsString();

        mockMvc.perform(loginRequest("nobody@example.com", PASSWORD))
                .andExpect(status().isUnauthorized())
                .andExpect(content().string(wrongPasswordBody));
    }

    @Test
    void apiRequest_withoutToken_returns401() throws Exception {
        mockMvc.perform(get("/api/activities"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void apiRequest_withTamperedToken_returns401() throws Exception {
        signUp().andExpect(status().isCreated());
        String token = logIn(EMAIL, PASSWORD);
        String tampered = token.substring(0, token.length() - 2) + (token.endsWith("A") ? "BB" : "AA");

        mockMvc.perform(get("/api/activities").header("Authorization", "Bearer " + tampered))
                .andExpect(status().isUnauthorized());
    }

    private org.springframework.test.web.servlet.ResultActions signUp() throws Exception {
        return mockMvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + EMAIL + "\",\"password\":\"" + PASSWORD + "\",\"firstName\":\"Ann\",\"lastName\":\"Lee\"}"));
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder loginRequest(String email, String password) {
        return post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}");
    }

    private String logIn(String email, String password) throws Exception {
        String body = mockMvc.perform(loginRequest(email, password))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode json = objectMapper.readTree(body);
        return json.get("token").asText();
    }
}
