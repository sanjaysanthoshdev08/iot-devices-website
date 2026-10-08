package com.hykon.iot_backend.service;

import com.hykon.iot_backend.dto.LoginRequest;
import com.hykon.iot_backend.entity.RefreshToken;
import com.hykon.iot_backend.entity.User;
import com.hykon.iot_backend.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class LoginService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;

    public LoginService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            RefreshTokenService refreshTokenService
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.refreshTokenService = refreshTokenService;
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

        // Generate and persist refresh token.
        RefreshToken refreshToken =
                refreshTokenService.createRefreshToken(user);

        return new LoginResult(
                user,
                accessToken,
                refreshToken.getToken()
        );
    }

    public static class LoginResult {

        private final User user;
        private final String accessToken;
        private final String refreshToken;

        public LoginResult(
                User user,
                String accessToken,
                String refreshToken
        ) {
            this.user = user;
            this.accessToken = accessToken;
            this.refreshToken = refreshToken;
        }

        public User getUser() {
            return user;
        }

        public String getAccessToken() {
            return accessToken;
        }

        public String getRefreshToken() {
            return refreshToken;
        }
    }
}