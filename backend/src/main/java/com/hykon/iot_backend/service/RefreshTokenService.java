package com.hykon.iot_backend.service;

import com.hykon.iot_backend.entity.RefreshToken;
import com.hykon.iot_backend.exception.InvalidTokenException;
import com.hykon.iot_backend.entity.User;
import com.hykon.iot_backend.repository.RefreshTokenRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;

@Service
public class RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final long refreshTokenExpiration;

    private final SecureRandom secureRandom = new SecureRandom();

    public RefreshTokenService(
            RefreshTokenRepository refreshTokenRepository,
            @Value("${jwt.refresh-token-expiration}") long refreshTokenExpiration
    ) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.refreshTokenExpiration = refreshTokenExpiration;
    }

    @Transactional
    public RefreshToken createRefreshToken(User user) {

        byte[] randomBytes = new byte[64];
        secureRandom.nextBytes(randomBytes);

        String token = Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(randomBytes);

        RefreshToken refreshToken = new RefreshToken();

        refreshToken.setToken(token);
        refreshToken.setUser(user);
        refreshToken.setExpiresAt(
                LocalDateTime.now()
                        .plusNanos(refreshTokenExpiration * 1_000_000)
        );
        refreshToken.setRevoked(false);

        return refreshTokenRepository.save(refreshToken);
    }

    public boolean isTokenValid(RefreshToken refreshToken) {

        return !refreshToken.isRevoked()
                && refreshToken.getExpiresAt().isAfter(LocalDateTime.now());
    }

    public RefreshToken findByToken(String token) {

        return refreshTokenRepository.findByToken(token)
                .orElseThrow(() ->
                        new InvalidTokenException("Invalid refresh token"));
    }

    @Transactional
    public void revokeToken(RefreshToken refreshToken) {

        refreshToken.setRevoked(true);

        refreshTokenRepository.save(refreshToken);
    }

    @Transactional
    public RefreshToken rotateRefreshToken(RefreshToken oldRefreshToken) {

        if (!isTokenValid(oldRefreshToken)) {
            throw new InvalidTokenException(
                    "Refresh token is expired or revoked"
            );
        }

        User user = oldRefreshToken.getUser();

        // Revoke the old refresh token so it cannot be reused.
        revokeToken(oldRefreshToken);

        // Create and persist a completely new refresh token.
        return createRefreshToken(user);
    }
}