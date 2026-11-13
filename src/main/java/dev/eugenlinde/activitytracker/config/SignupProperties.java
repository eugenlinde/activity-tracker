package dev.eugenlinde.activitytracker.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@Setter
@Getter
@ConfigurationProperties(prefix = "signup")
public class SignupProperties {
    private List<String> allowedEmails = new ArrayList<>();
}
