package com.hykon.iot_backend.service;

import com.hykon.iot_backend.entity.LoginSession;
import com.hykon.iot_backend.entity.RefreshToken;
import com.hykon.iot_backend.entity.User;
import com.hykon.iot_backend.exception.InvalidTokenException;
import com.hykon.iot_backend.repository.RefreshTokenRepository;
import com.hykon.iot_backend.repository.RoleRepository;
import com.hykon.iot_backend.repository.UserRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {

    private static final UUID SESSION_ID = UUID.randomUUID();

    @Mock
    private RefreshTokenRepository tokenRepository;

    @Mock
    private LoginSessionService sessionService;

    private RefreshTokenService service;

    @BeforeEach
    void setUp() {
        service = new RefreshTokenService(tokenRepository, sessionService);
    }

    private User user(Long id) {
        User user = new User();
        ReflectionTestUtils.setField(user, "id", id);
        user.setStatus("ACTIVE");
        return user;
    }

    private LoginSession session(User user) {
        LoginSession session = new LoginSession();
        ReflectionTestUtils.setField(session, "id", SESSION_ID);
        session.setUser(user);
        session.setExpiresAt(LocalDateTime.now().plusMinutes(5));
        session.setRevoked(false);
        return session;
    }

    private RefreshToken token() {
        User user = user(16L);
        LoginSession session = session(user);

        RefreshToken token = new RefreshToken();
        token.setToken("old-test-refresh-token");
        token.setUser(user);
        token.setSession(session);
        token.setExpiresAt(session.getExpiresAt());
        token.setRevoked(false);
        return token;
    }

    @Test
    void newRefreshTokenUsesNewlyCreatedSession() {
        User user = user(16L);
        LoginSession session = session(user);

        when(sessionService.create(user)).thenReturn(session);
        when(tokenRepository.save(any(RefreshToken.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        RefreshToken created = service.createRefreshToken(user);

        assertSame(user, created.getUser());
        assertSame(session, created.getSession());
        assertEquals(session.getExpiresAt(), created.getExpiresAt());
        assertFalse(created.isRevoked());
        assertNotNull(created.getToken());
        assertFalse(created.getToken().isBlank());

        var order = inOrder(sessionService, tokenRepository);
        order.verify(sessionService).create(user);
        order.verify(tokenRepository).save(created);
    }

    @Test
    void rotationReplacesTokenButRetainsSession() {
        RefreshToken oldToken = token();
        LoginSession session = oldToken.getSession();

        when(sessionService.renew(SESSION_ID, 16L))
                .thenReturn(session);
        when(tokenRepository.save(any(RefreshToken.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        RefreshToken replacement = service.rotateRefreshToken(oldToken);

        assertTrue(oldToken.isRevoked());
        assertFalse(replacement.isRevoked());
        assertSame(session, replacement.getSession());
        assertEquals(SESSION_ID, replacement.getSession().getId());
        assertEquals(session.getExpiresAt(), replacement.getExpiresAt());
        assertNotEquals(oldToken.getToken(), replacement.getToken());
        assertFalse(session.isRevoked());
        assertTrue(service.isTokenValid(replacement));
        assertFalse(service.isTokenValid(oldToken));

        verify(sessionService).renew(SESSION_ID, 16L);
        verify(sessionService, never()).create(any());
        verify(sessionService, never()).revoke(any(), any());

        var order = inOrder(tokenRepository);
        order.verify(tokenRepository).save(oldToken);
        order.verify(tokenRepository).save(replacement);
    }

    @Test
    void revokedRefreshTokenCannotBeRotated() {
        RefreshToken token = token();
        token.setRevoked(true);

        assertThrows(InvalidTokenException.class,
                () -> service.rotateRefreshToken(token));

        verifyNoInteractions(sessionService, tokenRepository);
    }

    @Test
    void expiredRefreshTokenCannotBeRotated() {
        RefreshToken token = token();
        token.setExpiresAt(LocalDateTime.now().minusMinutes(1));

        assertThrows(InvalidTokenException.class,
                () -> service.rotateRefreshToken(token));

        verifyNoInteractions(sessionService, tokenRepository);
    }

    @Test
    void revokedSessionPreventsRefresh() {
        RefreshToken token = token();
        token.getSession().setRevoked(true);

        assertThrows(InvalidTokenException.class,
                () -> service.rotateRefreshToken(token));

        verifyNoInteractions(sessionService, tokenRepository);
    }

    @Test
    void expiredSessionPreventsRefresh() {
        RefreshToken token = token();
        token.getSession().setExpiresAt(
                LocalDateTime.now().minusMinutes(1)
        );

        assertThrows(InvalidTokenException.class,
                () -> service.rotateRefreshToken(token));

        verifyNoInteractions(sessionService, tokenRepository);
    }

    @Test
    void legacyTokenWithoutSessionCannotRefresh() {
        RefreshToken token = token();
        token.setSession(null);

        assertThrows(InvalidTokenException.class,
                () -> service.rotateRefreshToken(token));

        verifyNoInteractions(sessionService, tokenRepository);
    }

    @Test
    void sessionMustBelongToRefreshTokenAccount() {
        RefreshToken token = token();
        token.getSession().setUser(user(19L));

        assertThrows(InvalidTokenException.class,
                () -> service.rotateRefreshToken(token));

        verifyNoInteractions(sessionService, tokenRepository);
    }

    @Test
    void logoutLocksTokenAndRevokesTokenAndSession() {
        RefreshToken token = token();

        when(tokenRepository.findByTokenForUpdate(token.getToken()))
                .thenReturn(Optional.of(token));

        doAnswer(invocation -> {
            token.getSession().setRevoked(true);
            return null;
        }).when(sessionService).revoke(SESSION_ID, 16L);

        AuthService authService = new AuthService(
                mock(UserRepository.class),
                mock(RoleRepository.class),
                mock(PasswordEncoder.class),
                service
        );

        authService.logout(token.getToken());

        assertTrue(token.isRevoked());
        assertTrue(token.getSession().isRevoked());
        assertFalse(service.isTokenValid(token));

        var order = inOrder(tokenRepository, sessionService);
        order.verify(tokenRepository)
                .findByTokenForUpdate(token.getToken());
        order.verify(sessionService).revoke(SESSION_ID, 16L);
        order.verify(tokenRepository).save(token);

        verify(sessionService, never()).renew(any(), any());
    }
}