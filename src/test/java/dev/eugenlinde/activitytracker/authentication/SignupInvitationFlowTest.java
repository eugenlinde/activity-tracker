package dev.eugenlinde.activitytracker.authentication;

import dev.eugenlinde.activitytracker.TestcontainersConfiguration;
import dev.eugenlinde.activitytracker.user.UserRepository;
import dev.eugenlinde.activitytracker.user.model.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestPropertySource(properties = "signup.allowed-emails=invited@example.com")
@Transactional
class SignupInvitationFlowTest {
    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserRepository userRepository;

    @Test
    void signup_whenEmailNotInvited_returns403AndCreatesNoUser() throws Exception {
        signUp("stranger@example.com")
                .andExpect(status().isForbidden())
                .andExpect(content().string("Sign-up is by invitation only"));

        assertFalse(userRepository.existsByEmail("stranger@example.com"));
    }

    @Test
    void signup_whenEmailInvited_returns201() throws Exception {
        signUp("invited@example.com").andExpect(status().isCreated());

        assertTrue(userRepository.existsByEmail("invited@example.com"));
    }

    @Test
    void signup_whenEmailInvitedInDifferentCase_returns201() throws Exception {
        signUp("Invited@Example.com").andExpect(status().isCreated());
    }

    @Test
    void signup_whenNotInvited_andEmailAlreadyRegistered_stillReturns403() throws Exception {
        User existing = new User();
        existing.setEmail("stranger@example.com");
        existing.setFirstName("First");
        existing.setLastName("Last");
        existing.setPassword("hash");
        existing.setEnabled(true);
        userRepository.saveAndFlush(existing);

        signUp("stranger@example.com")
                .andExpect(status().isForbidden())
                .andExpect(content().string("Sign-up is by invitation only"));
    }

    private ResultActions signUp(String email) throws Exception {
        return mockMvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"correct-horse\",\"firstName\":\"Ann\",\"lastName\":\"Lee\"}"));
    }
}
