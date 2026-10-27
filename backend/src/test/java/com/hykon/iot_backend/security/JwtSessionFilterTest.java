package com.hykon.iot_backend.security;

import com.hykon.iot_backend.entity.LoginSession;
import com.hykon.iot_backend.entity.Role;
import com.hykon.iot_backend.entity.RoleName;
import com.hykon.iot_backend.entity.User;
import com.hykon.iot_backend.repository.LoginSessionRepository;
import com.hykon.iot_backend.repository.UserRepository;
import com.hykon.iot_backend.service.JwtService;
import com.hykon.iot_backend.service.LoginSessionService;

import jakarta.servlet.FilterChain;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JwtSessionFilterTest {

    private static final String TOKEN = "test-access-token";
    private static final String EMAIL = "customer@example.com";
    private static final UUID SESSION_ID = UUID.randomUUID();

    @Mock
    private JwtService jwtService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private LoginSessionRepository sessionRepository;

    @Mock
    private FilterChain chain;

    private JwtAuthenticationFilter filter;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();

        LoginSessionService sessionService =
                new LoginSessionService(sessionRepository, 60_000);

        filter = new JwtAuthenticationFilter(
                jwtService, userRepository, sessionService
        );

        request = new MockHttpServletRequest();
        request.setMethod("GET");
        request.setRequestURI("/api/v1/devices");
        request.addHeader("Authorization", "Bearer " + TOKEN);

        response = new MockHttpServletResponse();
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private void allowToken() {
        when(jwtService.isTokenValid(TOKEN)).thenReturn(true);
        when(jwtService.extractEmail(TOKEN)).thenReturn(EMAIL);
        when(jwtService.extractSessionId(TOKEN)).thenReturn(SESSION_ID);
    }

    private User allowAccount() {
        User user = new User();
        ReflectionTestUtils.setField(user, "id", 16L);
        user.setEmail(EMAIL);
        user.setStatus("ACTIVE");
        user.setRole(new Role(RoleName.USER));

        when(userRepository.findByEmailWithRole(EMAIL))
                .thenReturn(Optional.of(user));

        return user;
    }

    private LoginSession allowSession(User user) {
        LoginSession session = new LoginSession();
        ReflectionTestUtils.setField(session, "id", SESSION_ID);
        session.setUser(user);
        session.setExpiresAt(LocalDateTime.now().plusMinutes(5));
        session.setRevoked(false);

        when(sessionRepository.findByIdAndUser_Id(SESSION_ID, 16L))
                .thenReturn(Optional.of(session));

        return session;
    }

    private void assertUnauthorized() throws Exception {
        filter.doFilter(request, response, chain);

        assertEquals(401, response.getStatus());
        assertTrue(response.getContentAsString()
                .contains("Invalid or expired access token"));
        assertNull(SecurityContextHolder.getContext().getAuthentication());

        verifyNoInteractions(chain);
    }

    @Test
    void activeSessionAuthenticatesWithDatabaseRoleAndSessionId()
            throws Exception {
        allowToken();
        User user = allowAccount();
        allowSession(user);

        filter.doFilter(request, response, chain);

        var authentication =
                SecurityContextHolder.getContext().getAuthentication();

        assertNotNull(authentication);
        assertTrue(authentication.isAuthenticated());
        assertEquals(EMAIL, authentication.getName());
        assertEquals(SESSION_ID, authentication.getDetails());
        assertTrue(authentication.getAuthorities().stream()
                .anyMatch(authority ->
                        authority.getAuthority().equals("ROLE_USER")));

        verify(chain).doFilter(request, response);
        verify(sessionRepository)
                .findByIdAndUser_Id(SESSION_ID, 16L);
        verify(jwtService, never()).extractRole(TOKEN);
    }

    @Test
    void revokedSessionRejectsOtherwiseValidAccessToken()
            throws Exception {
        allowToken();
        User user = allowAccount();
        LoginSession session = allowSession(user);
        session.setRevoked(true);

        assertUnauthorized();
    }

    @Test
    void expiredSessionRejectsOtherwiseValidAccessToken()
            throws Exception {
        allowToken();
        User user = allowAccount();
        LoginSession session = allowSession(user);
        session.setExpiresAt(LocalDateTime.now().minusMinutes(1));

        assertUnauthorized();
    }

    @Test
    void unknownOrUnownedSessionIsRejected()
            throws Exception {
        allowToken();
        allowAccount();

        assertUnauthorized();

        verify(sessionRepository)
                .findByIdAndUser_Id(SESSION_ID, 16L);
    }

    @Test
    void tokenWithoutSessionIdIsRejectedBeforeDatabaseLookup()
            throws Exception {
        when(jwtService.isTokenValid(TOKEN)).thenReturn(true);
        when(jwtService.extractEmail(TOKEN)).thenReturn(EMAIL);
        when(jwtService.extractSessionId(TOKEN))
                .thenThrow(new IllegalArgumentException(
                        "Login session ID is missing"
                ));

        assertUnauthorized();

        verifyNoInteractions(userRepository, sessionRepository);
    }

    @Test
    void invalidJwtIsRejectedBeforeDatabaseLookup()
            throws Exception {
        when(jwtService.isTokenValid(TOKEN)).thenReturn(false);

        assertUnauthorized();

        verifyNoInteractions(userRepository, sessionRepository);
    }

    @Test
    void inactiveAccountIsRejectedBeforeSessionLookup()
            throws Exception {
        allowToken();
        User user = allowAccount();
        user.setStatus("INACTIVE");

        assertUnauthorized();

        verifyNoInteractions(sessionRepository);
    }

    @Test
    void deletedAccountIsRejectedBeforeSessionLookup()
            throws Exception {
        allowToken();

        assertUnauthorized();

        verifyNoInteractions(sessionRepository);
    }

    @Test
    void databaseFailureIsNotMisreportedAsInvalidToken()
            throws Exception {
        allowToken();
        allowAccount();

        when(sessionRepository.findByIdAndUser_Id(SESSION_ID, 16L))
                .thenThrow(new DataAccessResourceFailureException(
                        "Test database failure"
                ));

        assertThrows(
                DataAccessResourceFailureException.class,
                () -> filter.doFilter(request, response, chain)
        );

        verifyNoInteractions(chain);
    }
}