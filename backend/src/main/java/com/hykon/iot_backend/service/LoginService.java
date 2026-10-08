package com.hykon.iot_backend.service;

import com.hykon.iot_backend.dto.LoginRequest;
import com.hykon.iot_backend.entity.User;
import com.hykon.iot_backend.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class LoginService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public LoginService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    public LoginResult login(LoginRequest request) {

        String email = request.getEmail().trim().toLowerCase();

        User user = userRepository.findByEmail(email)
                .orElseThrow(() ->
                        new IllegalArgumentException("Invalid email or password"));

        if (!"ACTIVE".equalsIgnoreCase(user.getStatus())) {
            throw new IllegalArgumentException("User account is not active");
        }

        if (!passwordEncoder.matches(
                request.getPassword(),
                user.getPassword()
        )) {
            throw new IllegalArgumentException("Invalid email or password");
        }

        // Generate JWT access token after successful authentication.
        String accessToken = jwtService.generateAccessToken(user);

        return new LoginResult(user, accessToken);
    }

    public static class LoginResult {

        private final User user;
        private final String accessToken;

        public LoginResult(User user, String accessToken) {
            this.user = user;
            this.accessToken = accessToken;
        }

        public User getUser() {
            return user;
        }

        public String getAccessToken() {
            return accessToken;
        }
    }
}