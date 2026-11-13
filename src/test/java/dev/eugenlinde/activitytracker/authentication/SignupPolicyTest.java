package dev.eugenlinde.activitytracker.authentication;

import dev.eugenlinde.activitytracker.config.SignupProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SignupPolicyTest {

    @Test
    void emptyList_allowsNobody() {
        assertFalse(policy(List.of()).isAllowed("a@example.com"));
    }

    @Test
    void wildcard_allowsAnyone() {
        assertTrue(policy(List.of("*")).isAllowed("anyone@example.com"));
    }

    @Test
    void listedAddresses_areAllowed_othersAreNot() {
        SignupPolicy policy = policy(List.of("a@example.com", "b@example.com"));

        assertTrue(policy.isAllowed("a@example.com"));
        assertTrue(policy.isAllowed("b@example.com"));
        assertFalse(policy.isAllowed("c@example.com"));
    }

    @Test
    void matching_ignoresCaseAndSurroundingSpaces() {
        SignupPolicy policy = policy(List.of(" A@Example.com "));

        assertTrue(policy.isAllowed("a@example.com"));
        assertTrue(policy.isAllowed("  A@EXAMPLE.COM"));
    }

    @Test
    void nullEmail_isNotAllowed() {
        assertFalse(policy(List.of("*")).isAllowed(null));
    }

    @Test
    void blankProperty_bindsToAnEmptyList_soSignupIsClosed() {
        SignupProperties properties = bind(Map.of("signup.allowed-emails", ""));

        assertFalse(new SignupPolicy(properties).isAllowed("a@example.com"));
    }

    @Test
    void missingProperty_meansSignupIsClosed() {
        assertFalse(new SignupPolicy(new SignupProperties()).isAllowed("a@example.com"));
    }

    @Test
    void commaSeparatedProperty_bindsToTheList() {
        SignupProperties properties = bind(Map.of("signup.allowed-emails", "a@example.com, b@example.com"));
        SignupPolicy policy = new SignupPolicy(properties);

        assertTrue(policy.isAllowed("b@example.com"));
        assertFalse(policy.isAllowed("c@example.com"));
    }

    private SignupPolicy policy(List<String> allowedEmails) {
        SignupProperties properties = new SignupProperties();
        properties.setAllowedEmails(allowedEmails);
        return new SignupPolicy(properties);
    }

    private SignupProperties bind(Map<String, String> source) {
        return new Binder(new MapConfigurationPropertySource(source))
                .bind("signup", SignupProperties.class)
                .orElseGet(SignupProperties::new);
    }
}
