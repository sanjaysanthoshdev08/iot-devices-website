package com.hykon.iot_backend.service;

import com.hykon.iot_backend.entity.LoginSession;
import com.hykon.iot_backend.entity.RefreshToken;
import com.hykon.iot_backend.entity.User;
import com.hykon.iot_backend.exception.InvalidTokenException;
import com.hykon.iot_backend.repository.RefreshTokenRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;

@Service
public class RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final LoginSessionService loginSessionService;

    private final SecureRandom secureRandom = new SecureRandom();

    public RefreshTokenService(
            RefreshTokenRepository refreshTokenRepository,
            LoginSessionService loginSessionService
    ) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.loginSessionService = loginSessionService;
    }

    @Transactional
    public RefreshToken createRefreshToken(User user) {
        LoginSession session = loginSessionService.create(user);
        return createForSession(user, session);
    }

    public boolean isTokenValid(RefreshToken refreshToken) {
        if (refreshToken == null
                || refreshToken.isRevoked()
                || refreshToken.getExpiresAt() == null
                || !refreshToken.getExpiresAt()
                        .isAfter(LocalDateTime.now())) {
            return false;
        }

        LoginSession session = refreshToken.getSession();

        return session != null
                && !session.isRevoked()
                && session.getExpiresAt() != null
                && session.getExpiresAt().isAfter(LocalDateTime.now())
                && session.getUser() != null
                && refreshToken.getUser() != null
                && session.getUser().getId() != null
                && session.getUser().getId().equals(
                        refreshToken.getUser().getId()
                );
    }

    public RefreshToken findByToken(String token) {
        return refreshTokenRepository.findByToken(token)
                .orElseThrow(() ->
                        new InvalidTokenException("Invalid refresh token"));
    }

    @Transactional
    public RefreshToken findByTokenForUpdate(String token) {
        return refreshTokenRepository.findByTokenForUpdate(token)
                .orElseThrow(() ->
                        new InvalidTokenException("Invalid refresh token"));
    }

    @Transactional
    public void revokeToken(RefreshToken refreshToken) {
        refreshToken.setRevoked(true);
        refreshTokenRepository.save(refreshToken);
    }

    @Transactional
    public void revokeTokenAndSession(RefreshToken refreshToken) {
        if (refreshToken.getSession() == null
                || refreshToken.getUser() == null) {
            throw new InvalidTokenException("Login session is missing");
        }

        loginSessionService.revoke(
                refreshToken.getSession().getId(),
                refreshToken.getUser().getId()
        );

        revokeToken(refreshToken);
    }

    @Transactional
    public RefreshToken rotateRefreshToken(RefreshToken oldRefreshToken) {
        if (!isTokenValid(oldRefreshToken)) {
            throw new InvalidTokenException(
                    "Refresh token is expired or revoked"
            );
        }

        User user = oldRefreshToken.getUser();

        LoginSession session = loginSessionService.renew(
                oldRefreshToken.getSession().getId(),
                user.getId()
        );

        // Rotation revokes the old token, while retaining its session.
        revokeToken(oldRefreshToken);

        return createForSession(user, session);
    }

    private RefreshToken createForSession(
            User user,
            LoginSession session
    ) {
        byte[] randomBytes = new byte[64];
        secureRandom.nextBytes(randomBytes);

        String token = Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(randomBytes);

        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setToken(token);
        refreshToken.setUser(user);
        refreshToken.setSession(session);
        refreshToken.setExpiresAt(session.getExpiresAt());
        refreshToken.setRevoked(false);

        return refreshTokenRepository.save(refreshToken);
    }
}