package dev.eugenlinde.activitytracker.authentication;

import dev.eugenlinde.activitytracker.config.SignupProperties;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class SignupPolicy {
    private static final String ANYONE = "*";

    private final Set<String> allowedEmails;

    public SignupPolicy(SignupProperties signupProperties) {
        this.allowedEmails = signupProperties.getAllowedEmails().stream()
                .map(SignupPolicy::normalize)
                .filter(email -> !email.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
    }

    public boolean isAllowed(String email) {
        if (email == null) {
            return false;
        }
        return allowedEmails.contains(ANYONE) || allowedEmails.contains(normalize(email));
    }

    private static String normalize(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
