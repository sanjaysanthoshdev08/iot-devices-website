package com.hykon.iot_backend.service;

import com.hykon.iot_backend.dto.LoginRequest;
import com.hykon.iot_backend.entity.LoginSession;
import com.hykon.iot_backend.entity.RefreshToken;
import com.hykon.iot_backend.entity.User;
import com.hykon.iot_backend.exception.InvalidCredentialsException;
import com.hykon.iot_backend.exception.InvalidTokenException;
import com.hykon.iot_backend.repository.UserRepository;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LoginServiceTest {

    private static final UUID SESSION_ID = UUID.randomUUID();

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtService jwtService;

    @Mock
    private RefreshTokenService refreshTokenService;

    @InjectMocks
    private LoginService loginService;

    private User account(String status) {
        User user = new User();
        ReflectionTestUtils.setField(user, "id", 16L);
        user.setEmail("customer@example.com");
        user.setPassword("stored-test-hash");
        user.setStatus(status);
        return user;
    }

    private LoginRequest request() {
        LoginRequest request = new LoginRequest();
        request.setEmail(" Customer@Example.com ");
        request.setPassword("test-password");
        return request;
    }

    private RefreshToken token(User user, String value) {
        LoginSession session = new LoginSession();
        ReflectionTestUtils.setField(session, "id", SESSION_ID);
        session.setUser(user);

        RefreshToken token = new RefreshToken();
        token.setToken(value);
        token.setUser(user);
        token.setSession(session);
        return token;
    }

    @Test
    void refreshRejectsInactiveUserWithoutIssuingNewTokens() {
        User inactiveUser = account("INACTIVE");
        RefreshToken oldRefreshToken =
                token(inactiveUser, "test-refresh-token");

        when(refreshTokenService.findByTokenForUpdate(
                "test-refresh-token"
        )).thenReturn(oldRefreshToken);

        when(refreshTokenService.isTokenValid(oldRefreshToken))
                .thenReturn(true);

        InvalidTokenException exception = assertThrows(
                InvalidTokenException.class,
                () -> loginService.refresh("test-refresh-token")
        );

        assertEquals(
                "The user account is inactive",
                exception.getMessage()
        );

        verifyNoInteractions(jwtService);
        verify(refreshTokenService, never())
                .rotateRefreshToken(any(RefreshToken.class));
    }

    @Test
    void loginIssuesJwtForCreatedSession() {
        User user = account("ACTIVE");
        RefreshToken refreshToken = token(user, "new-refresh");

        when(userRepository.findByEmail("customer@example.com"))
                .thenReturn(Optional.of(user));
        when(passwordEncoder.matches("test-password", "stored-test-hash"))
                .thenReturn(true);
        when(refreshTokenService.createRefreshToken(user))
                .thenReturn(refreshToken);
        when(jwtService.generateAccessToken(user, SESSION_ID))
                .thenReturn("session-access-token");

        LoginService.LoginResult result = loginService.login(request());

        assertSame(user, result.getUser());
        assertEquals("session-access-token", result.getAccessToken());
        assertEquals("new-refresh", result.getRefreshToken());

        var order = inOrder(refreshTokenService, jwtService);
        order.verify(refreshTokenService).createRefreshToken(user);
        order.verify(jwtService).generateAccessToken(user, SESSION_ID);

        verify(jwtService, never()).generateAccessToken(any(User.class));
    }

    @Test
    void refreshIssuesJwtForRotatedTokensSession() {
        User user = account("ACTIVE");
        RefreshToken oldToken = token(user, "old-refresh");
        RefreshToken newToken = token(user, "new-refresh");

        when(refreshTokenService.findByTokenForUpdate("old-refresh"))
                .thenReturn(oldToken);
        when(refreshTokenService.isTokenValid(oldToken))
                .thenReturn(true);
        when(refreshTokenService.rotateRefreshToken(oldToken))
                .thenReturn(newToken);
        when(jwtService.generateAccessToken(user, SESSION_ID))
                .thenReturn("renewed-access");

        LoginService.RefreshResult result =
                loginService.refresh("old-refresh");

        assertEquals("renewed-access", result.getAccessToken());
        assertEquals("new-refresh", result.getRefreshToken());

        var order = inOrder(refreshTokenService, jwtService);
        order.verify(refreshTokenService)
                .findByTokenForUpdate("old-refresh");
        order.verify(refreshTokenService).isTokenValid(oldToken);
        order.verify(refreshTokenService).rotateRefreshToken(oldToken);
        order.verify(jwtService).generateAccessToken(user, SESSION_ID);

        verify(jwtService, never()).generateAccessToken(any(User.class));
    }

    @Test
    void invalidRefreshTokenCannotIssueReplacementTokens() {
        User user = account("ACTIVE");
        RefreshToken oldToken = token(user, "old-refresh");

        when(refreshTokenService.findByTokenForUpdate("old-refresh"))
                .thenReturn(oldToken);
        when(refreshTokenService.isTokenValid(oldToken))
                .thenReturn(false);

        assertThrows(InvalidTokenException.class,
                () -> loginService.refresh("old-refresh"));

        verifyNoInteractions(jwtService);
        verify(refreshTokenService, never())
                .rotateRefreshToken(any(RefreshToken.class));
    }

    @Test
    void inactiveLoginCannotCreateSessionOrTokens() {
        User user = account("INACTIVE");
        when(userRepository.findByEmail("customer@example.com"))
                .thenReturn(Optional.of(user));

        assertThrows(InvalidCredentialsException.class,
                () -> loginService.login(request()));

        verifyNoInteractions(
                passwordEncoder, refreshTokenService, jwtService
        );
    }

    @Test
    void wrongPasswordCannotCreateSessionOrTokens() {
        User user = account("ACTIVE");
        when(userRepository.findByEmail("customer@example.com"))
                .thenReturn(Optional.of(user));
        when(passwordEncoder.matches("test-password", "stored-test-hash"))
                .thenReturn(false);

        assertThrows(InvalidCredentialsException.class,
                () -> loginService.login(request()));

        verifyNoInteractions(refreshTokenService, jwtService);
    }

    @Test
    void unknownAccountCannotCreateSessionOrTokens() {
        assertThrows(InvalidCredentialsException.class,
                () -> loginService.login(request()));

        verifyNoInteractions(
                passwordEncoder, refreshTokenService, jwtService
        );
    }
}