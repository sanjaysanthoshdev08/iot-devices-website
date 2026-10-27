
package com.hykon.iot_backend.service;

import com.hykon.iot_backend.dto.LoginRequest;
import com.hykon.iot_backend.exception.InvalidCredentialsException;
import com.hykon.iot_backend.exception.InvalidTokenException;
import com.hykon.iot_backend.entity.RefreshToken;
import com.hykon.iot_backend.entity.User;
import com.hykon.iot_backend.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    @Transactional
    public LoginResult login(LoginRequest request) {

        String email = request.getEmail().trim().toLowerCase();

        User user = userRepository.findByEmail(email)
                .orElseThrow(() ->
                        new InvalidCredentialsException(
                                "Invalid email or password"
                        ));

        if (!"ACTIVE".equalsIgnoreCase(user.getStatus())) {
            throw new InvalidCredentialsException(
                    "Invalid email or password"
            );
        }

        if (!passwordEncoder.matches(
                request.getPassword(),
                user.getPassword()
        )) {
            throw new InvalidCredentialsException(
                    "Invalid email or password"
            );
        }

        // Persist the session and refresh token before issuing its JWT.
        RefreshToken refreshToken =
                refreshTokenService.createRefreshToken(user);

        String accessToken = jwtService.generateAccessToken(
                user,
                refreshToken.getSession().getId()
        );

        return new LoginResult(
                user,
                accessToken,
                refreshToken.getToken()
        );
    }

    @Transactional
    public RefreshResult refresh(String refreshTokenValue) {

        // Find and lock the existing refresh-token row.
        RefreshToken oldRefreshToken =
                refreshTokenService.findByTokenForUpdate(
                        refreshTokenValue
                );

        // Validate that the token is active and not expired.
        if (!refreshTokenService.isTokenValid(oldRefreshToken)) {
            throw new InvalidTokenException(
                    "Refresh token is expired or revoked"
            );
        }

        // Get the user associated with the refresh token.
        User user = oldRefreshToken.getUser();

        // Do not issue new tokens to inactive accounts.
        if (!"ACTIVE".equalsIgnoreCase(user.getStatus())) {
            throw new InvalidTokenException(
                    "The user account is inactive"
            );
        }

        // Rotate the refresh token while retaining the login session.
        RefreshToken newRefreshToken =
                refreshTokenService.rotateRefreshToken(oldRefreshToken);

        String accessToken = jwtService.generateAccessToken(
                user,
                newRefreshToken.getSession().getId()
        );

        return new RefreshResult(
                accessToken,
                newRefreshToken.getToken()
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

    public static class RefreshResult {

        private final String accessToken;
        private final String refreshToken;

        public RefreshResult(
                String accessToken,
                String refreshToken
        ) {
            this.accessToken = accessToken;
            this.refreshToken = refreshToken;
        }

        public String getAccessToken() {
            return accessToken;
        }

        public String getRefreshToken() {
            return refreshToken;
        }
    }
}
