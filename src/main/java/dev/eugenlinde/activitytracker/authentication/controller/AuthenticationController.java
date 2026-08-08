package dev.eugenlinde.activitytracker.authentication.controller;

import dev.eugenlinde.activitytracker.authentication.dto.LoginRequest;
import dev.eugenlinde.activitytracker.authentication.dto.LoginResponse;
import dev.eugenlinde.activitytracker.authentication.dto.SignupRequest;
import dev.eugenlinde.activitytracker.authentication.service.AuthenticationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthenticationController {
    private final AuthenticationService authenticationService;

    public AuthenticationController(AuthenticationService authenticationService) {
        this.authenticationService = authenticationService;
    }

    @PostMapping("/signup")
    public ResponseEntity<?> signup(@RequestBody @Valid SignupRequest signupRequest) {
        authenticationService.signUp(signupRequest);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@RequestBody @Valid LoginRequest loginRequest) {
        String token = authenticationService.logIn(loginRequest);
        return ResponseEntity.ok(new LoginResponse(token));
    }
}
