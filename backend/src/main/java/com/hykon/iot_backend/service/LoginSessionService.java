package com.hykon.iot_backend.service;

import com.hykon.iot_backend.entity.LoginSession;
import com.hykon.iot_backend.entity.User;
import com.hykon.iot_backend.exception.InvalidTokenException;
import com.hykon.iot_backend.repository.LoginSessionRepository;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class LoginSessionService {

    private final LoginSessionRepository sessionRepository;
    private final long sessionExpirationMillis;

    public LoginSessionService(
            LoginSessionRepository sessionRepository,
            @Value("${jwt.refresh-token-expiration}")
            long sessionExpirationMillis
    ) {
        if (sessionExpirationMillis <= 0) {
            throw new IllegalArgumentException(
                    "Session expiration must be greater than zero"
            );
        }

        this.sessionRepository = sessionRepository;
        this.sessionExpirationMillis = sessionExpirationMillis;
    }

    @Transactional
    public LoginSession create(User user) {
        if (user == null
                || user.getId() == null
                || !"ACTIVE".equalsIgnoreCase(user.getStatus())) {
            throw invalidSession();
        }

        LoginSession session = new LoginSession();
        session.setUser(user);
        session.setExpiresAt(nextExpiration());
        session.setRevoked(false);

        return sessionRepository.save(session);
    }

    public LoginSession requireActive(UUID sessionId, Long userId) {
        requireIdentity(sessionId, userId);

        LoginSession session = sessionRepository
                .findByIdAndUser_Id(sessionId, userId)
                .orElseThrow(this::invalidSession);

        requireNotExpiredOrRevoked(session);
        return session;
    }

    @Transactional
    public LoginSession requireActiveForUpdate(
            UUID sessionId,
            Long userId
    ) {
        LoginSession session = findOwnedForUpdate(sessionId, userId);
        requireNotExpiredOrRevoked(session);
        return session;
    }

    @Transactional
    public LoginSession renew(UUID sessionId, Long userId) {
        LoginSession session = findOwnedForUpdate(sessionId, userId);

        requireNotExpiredOrRevoked(session);
        session.setExpiresAt(nextExpiration());

        return sessionRepository.save(session);
    }

    @Transactional
    public void revoke(UUID sessionId, Long userId) {
        LoginSession session = findOwnedForUpdate(sessionId, userId);

        if (!session.isRevoked()) {
            session.setRevoked(true);
            sessionRepository.save(session);
        }
    }

    private LoginSession findOwnedForUpdate(
            UUID sessionId,
            Long userId
    ) {
        requireIdentity(sessionId, userId);

        LoginSession session = sessionRepository
                .findByIdForUpdate(sessionId)
                .orElseThrow(this::invalidSession);

        if (session.getUser() == null
                || !userId.equals(session.getUser().getId())) {
            throw invalidSession();
        }

        return session;
    }

    private void requireIdentity(UUID sessionId, Long userId) {
        if (sessionId == null || userId == null) {
            throw invalidSession();
        }
    }

    private void requireNotExpiredOrRevoked(LoginSession session) {
        if (session.isRevoked()
                || session.getExpiresAt() == null
                || !session.getExpiresAt().isAfter(LocalDateTime.now())) {
            throw invalidSession();
        }
    }

    private LocalDateTime nextExpiration() {
        return LocalDateTime.now().plus(
                sessionExpirationMillis,
                ChronoUnit.MILLIS
        );
    }

    private InvalidTokenException invalidSession() {
        return new InvalidTokenException(
                "Login session is invalid, expired, or revoked"
        );
    }
}