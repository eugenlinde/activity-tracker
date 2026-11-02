package dev.eugenlinde.activitytracker.authentication.service;

import dev.eugenlinde.activitytracker.authentication.dto.LoginRequest;
import dev.eugenlinde.activitytracker.authentication.dto.SignupRequest;
import dev.eugenlinde.activitytracker.common.exception.UserExistsException;
import dev.eugenlinde.activitytracker.user.UserRepository;
import dev.eugenlinde.activitytracker.user.model.User;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class AuthenticationService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final TokenService tokenService;

    public AuthenticationService(UserRepository userRepository, PasswordEncoder passwordEncoder, AuthenticationManager authenticationManager, TokenService tokenService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.tokenService = tokenService;
    }

    public User signUp(SignupRequest signupRequest) {
        if (userRepository.existsByEmail(signupRequest.email())) {
            throw new UserExistsException();
        }
        User user = new User();
        user.setEmail(signupRequest.email());
        user.setPassword(passwordEncoder.encode(signupRequest.password()));
        user.setFirstName(signupRequest.firstName());
        user.setLastName(signupRequest.lastName());
        user.setEnabled(true);

        return userRepository.save(user);
    }

    public String logIn(LoginRequest loginRequest) {
        UsernamePasswordAuthenticationToken authenticationToken =
                UsernamePasswordAuthenticationToken
                        .unauthenticated(
                                loginRequest.email(),
                                loginRequest.password()
                        );

        Authentication authentication = authenticationManager.authenticate(authenticationToken);

        return tokenService.generateToken(authentication);
    }
}
