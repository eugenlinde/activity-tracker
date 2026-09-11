package dev.eugenlinde.activitytracker.authentication.service;

import dev.eugenlinde.activitytracker.user.model.SecurityUser;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Service
public class TokenService {
    private final JwtEncoder jwtEncoder;
    private final Duration expiration;

    public TokenService(JwtEncoder jwtEncoder, @Value("${jwt.expiration}") Duration expiration) {
        this.jwtEncoder = jwtEncoder;
        this.expiration = expiration;
    }

    public String generateToken(Authentication authentication) {
        SecurityUser user = (SecurityUser) authentication.getPrincipal();
        Instant now = Instant.now();
        String userId = user.getId().toString();
        List<String> authorities = user
                .getAuthorities()
                .stream()
                .map(GrantedAuthority::getAuthority)
                .toList();
        JwtClaimsSet jwtClaimsSet = JwtClaimsSet.builder()
                .subject(userId)
                .issuedAt(now)
                .expiresAt(now.plus(expiration))
                .claim("authorities", authorities)
                .build();

        return jwtEncoder
                .encode(JwtEncoderParameters.from(jwtClaimsSet))
                .getTokenValue();
    }
}
