package com.hykon.iot_backend.service;

import com.hykon.iot_backend.entity.LoginSession;
import com.hykon.iot_backend.entity.User;
import com.hykon.iot_backend.exception.InvalidTokenException;
import com.hykon.iot_backend.repository.LoginSessionRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LoginSessionServiceTest {

    private static final long EXPIRATION_MILLIS = 60_000;
    private static final UUID SESSION_ID = UUID.randomUUID();

    @Mock
    private LoginSessionRepository sessionRepository;

    private LoginSessionService service;

    @BeforeEach
    void setUp() {
        service = new LoginSessionService(
                sessionRepository, EXPIRATION_MILLIS
        );
    }

    private User user(Long id) {
        User user = new User();
        ReflectionTestUtils.setField(user, "id", id);
        user.setStatus("ACTIVE");
        return user;
    }

    private LoginSession session() {
        LoginSession session = new LoginSession();
        ReflectionTestUtils.setField(session, "id", SESSION_ID);
        session.setUser(user(16L));
        session.setExpiresAt(LocalDateTime.now().plusMinutes(5));
        session.setRevoked(false);
        return session;
    }

    private void allowLookup(LoginSession session) {
        when(sessionRepository.findByIdAndUser_Id(SESSION_ID, 16L))
                .thenReturn(Optional.of(session));
    }

    private void allowLockedLookup(LoginSession session) {
        when(sessionRepository.findByIdForUpdate(SESSION_ID))
                .thenReturn(Optional.of(session));
    }

    @Test
    void createsSessionForActiveAccountWithConfiguredLifetime() {
        User account = user(16L);
        when(sessionRepository.save(any(LoginSession.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        LocalDateTime before = LocalDateTime.now();
        LoginSession created = service.create(account);
        LocalDateTime after = LocalDateTime.now();

        assertSame(account, created.getUser());
        assertFalse(created.isRevoked());
        assertFalse(created.getExpiresAt().isBefore(
                before.plusSeconds(60)
        ));
        assertFalse(created.getExpiresAt().isAfter(
                after.plusSeconds(60)
        ));

        verify(sessionRepository).save(created);
    }

    @Test
    void rejectsMissingUnsavedAndInactiveAccountsOnCreation() {
        assertThrows(InvalidTokenException.class,
                () -> service.create(null));

        assertThrows(InvalidTokenException.class,
                () -> service.create(user(null)));

        User inactive = user(16L);
        inactive.setStatus("INACTIVE");

        assertThrows(InvalidTokenException.class,
                () -> service.create(inactive));

        verifyNoInteractions(sessionRepository);
    }

    @Test
    void activeSessionLookupIsScopedToAccount() {
        LoginSession session = session();
        allowLookup(session);

        assertSame(session, service.requireActive(SESSION_ID, 16L));

        verify(sessionRepository)
                .findByIdAndUser_Id(SESSION_ID, 16L);
        verifyNoMoreInteractions(sessionRepository);
    }

    @Test
    void unknownOrUnownedSessionIsRejected() {
        assertThrows(InvalidTokenException.class,
                () -> service.requireActive(SESSION_ID, 19L));

        verify(sessionRepository)
                .findByIdAndUser_Id(SESSION_ID, 19L);
        verifyNoMoreInteractions(sessionRepository);
    }

    @Test
    void revokedExpiredAndMissingExpirySessionsAreRejected() {
        LoginSession session = session();
        allowLookup(session);

        session.setRevoked(true);
        assertThrows(InvalidTokenException.class,
                () -> service.requireActive(SESSION_ID, 16L));

        session.setRevoked(false);
        session.setExpiresAt(LocalDateTime.now().minusMinutes(1));
        assertThrows(InvalidTokenException.class,
                () -> service.requireActive(SESSION_ID, 16L));

        session.setExpiresAt(null);
        assertThrows(InvalidTokenException.class,
                () -> service.requireActive(SESSION_ID, 16L));

        verify(sessionRepository, never()).save(any());
    }

    @Test
    void missingIdentityIsRejectedBeforeRepositoryAccess() {
        assertThrows(InvalidTokenException.class,
                () -> service.requireActive(null, 16L));
        assertThrows(InvalidTokenException.class,
                () -> service.requireActive(SESSION_ID, null));
        assertThrows(InvalidTokenException.class,
                () -> service.renew(null, 16L));
        assertThrows(InvalidTokenException.class,
                () -> service.revoke(SESSION_ID, null));

        verifyNoInteractions(sessionRepository);
    }

    @Test
    void renewalKeepsSameSessionAndSetsNewExpiry() {
        LoginSession session = session();
        session.setExpiresAt(LocalDateTime.now().plusSeconds(10));
        allowLockedLookup(session);

        when(sessionRepository.save(session)).thenReturn(session);

        LocalDateTime before = LocalDateTime.now();
        LoginSession renewed = service.renew(SESSION_ID, 16L);
        LocalDateTime after = LocalDateTime.now();

        assertSame(session, renewed);
        assertEquals(SESSION_ID, renewed.getId());
        assertFalse(renewed.isRevoked());
        assertFalse(renewed.getExpiresAt().isBefore(
                before.plusSeconds(60)
        ));
        assertFalse(renewed.getExpiresAt().isAfter(
                after.plusSeconds(60)
        ));

        verify(sessionRepository).findByIdForUpdate(SESSION_ID);
        verify(sessionRepository).save(session);
    }

    @Test
    void renewalRejectsAnotherAccountsSession() {
        LoginSession session = session();
        allowLockedLookup(session);
        LocalDateTime originalExpiry = session.getExpiresAt();

        assertThrows(InvalidTokenException.class,
                () -> service.renew(SESSION_ID, 19L));

        assertEquals(originalExpiry, session.getExpiresAt());
        verify(sessionRepository, never()).save(any());
    }

    @Test
    void renewalRejectsExpiredAndRevokedSessions() {
        LoginSession session = session();
        allowLockedLookup(session);

        session.setRevoked(true);
        assertThrows(InvalidTokenException.class,
                () -> service.renew(SESSION_ID, 16L));

        session.setRevoked(false);
        session.setExpiresAt(LocalDateTime.now().minusMinutes(1));
        assertThrows(InvalidTokenException.class,
                () -> service.renew(SESSION_ID, 16L));

        verify(sessionRepository, never()).save(any());
    }

    @Test
    void revocationMarksOwnedSessionRevoked() {
        LoginSession session = session();
        allowLockedLookup(session);

        service.revoke(SESSION_ID, 16L);

        assertTrue(session.isRevoked());
        verify(sessionRepository).findByIdForUpdate(SESSION_ID);
        verify(sessionRepository).save(session);
    }

    @Test
    void revocationCannotChangeAnotherAccountsSession() {
        LoginSession session = session();
        allowLockedLookup(session);

        assertThrows(InvalidTokenException.class,
                () -> service.revoke(SESSION_ID, 19L));

        assertFalse(session.isRevoked());
        verify(sessionRepository, never()).save(any());
    }

    @Test
    void repeatedRevocationDoesNotSaveAgain() {
        LoginSession session = session();
        session.setRevoked(true);
        allowLockedLookup(session);

        service.revoke(SESSION_ID, 16L);

        assertTrue(session.isRevoked());
        verify(sessionRepository, never()).save(any());
    }

    @Test
    void expiredSessionCanStillBeRevoked() {
        LoginSession session = session();
        session.setExpiresAt(LocalDateTime.now().minusMinutes(1));
        allowLockedLookup(session);

        service.revoke(SESSION_ID, 16L);

        assertTrue(session.isRevoked());
        verify(sessionRepository).save(session);
    }

    @Test
    void invalidExpirationConfigurationIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> new LoginSessionService(sessionRepository, 0));
        assertThrows(IllegalArgumentException.class,
                () -> new LoginSessionService(sessionRepository, -1));

        verifyNoInteractions(sessionRepository);
    }
}