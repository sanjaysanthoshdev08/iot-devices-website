package com.hykon.iot_backend.service;

import com.hykon.iot_backend.entity.AccessVerificationAttempt;
import com.hykon.iot_backend.repository.AccessVerificationAttemptRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AccessVerificationAttemptServiceTest {

    @Mock
    private AccessVerificationAttemptRepository repository;

    private AccessVerificationAttemptService service;

    @BeforeEach
    void setUp() {
        service = new AccessVerificationAttemptService(repository, 5, 60);
    }

    private AccessVerificationAttempt allowCounter(
            Long userId,
            int failures,
            LocalDateTime lockedUntil
    ) {
        AccessVerificationAttempt attempt = new AccessVerificationAttempt();
        attempt.setUserId(userId);
        attempt.setFailedAttempts(failures);
        attempt.setLockedUntil(lockedUntil);

        when(repository.findByUserIdForUpdate(userId))
                .thenReturn(Optional.of(attempt));

        return attempt;
    }

    @Test
    void firstAttemptIsReservedBeforeVerification() {
        AccessVerificationAttempt attempt = allowCounter(16L, 0, null);

        var decision = service.beginAttempt(16L);

        assertTrue(decision.allowed());
        assertEquals(4, decision.remainingAttempts());
        assertNull(decision.lockedUntil());
        assertEquals(1, attempt.getFailedAttempts());

        var order = inOrder(repository);
        order.verify(repository).initializeIfMissing(16L);
        order.verify(repository).findByUserIdForUpdate(16L);
        order.verify(repository).saveAndFlush(attempt);
    }

    @Test
    void fifthAttemptIsAllowedButFurtherAttemptsAreBlocked() {
        AccessVerificationAttempt attempt = allowCounter(16L, 4, null);

        var fifth = service.beginAttempt(16L);

        assertTrue(fifth.allowed());
        assertEquals(0, fifth.remainingAttempts());
        assertNotNull(fifth.lockedUntil());
        assertTrue(fifth.lockedUntil().isAfter(LocalDateTime.now()));

        var sixth = service.beginAttempt(16L);

        assertFalse(sixth.allowed());
        assertEquals(0, sixth.remainingAttempts());
        assertEquals(fifth.lockedUntil(), sixth.lockedUntil());
        assertEquals(5, attempt.getFailedAttempts());

        verify(repository, times(1)).saveAndFlush(attempt);
    }

    @Test
    void blockedRequestsDoNotExtendTheLockout() {
        LocalDateTime lockedUntil = LocalDateTime.now().plusMinutes(1);
        AccessVerificationAttempt attempt =
                allowCounter(16L, 5, lockedUntil);

        var decision = service.beginAttempt(16L);

        assertFalse(decision.allowed());
        assertEquals(lockedUntil, decision.lockedUntil());
        assertEquals(lockedUntil, attempt.getLockedUntil());
        assertEquals(5, attempt.getFailedAttempts());

        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void expiredLockStartsFreshAllowance() {
        AccessVerificationAttempt attempt = allowCounter(
                16L, 5, LocalDateTime.now().minusMinutes(1)
        );

        var decision = service.beginAttempt(16L);

        assertTrue(decision.allowed());
        assertEquals(4, decision.remainingAttempts());
        assertEquals(1, attempt.getFailedAttempts());
        assertNull(attempt.getLockedUntil());
        verify(repository).saveAndFlush(attempt);
    }

    @Test
    void successfulVerificationResetsCounterAndLock() {
        AccessVerificationAttempt attempt = allowCounter(
                16L, 5, LocalDateTime.now().plusMinutes(1)
        );

        service.recordSuccess(16L);

        assertEquals(0, attempt.getFailedAttempts());
        assertNull(attempt.getLockedUntil());
        verify(repository).findByUserIdForUpdate(16L);
        verify(repository).saveAndFlush(attempt);
    }

    @Test
    void accountsHaveIndependentCounters() {
        AccessVerificationAttempt first = allowCounter(16L, 3, null);
        AccessVerificationAttempt second = allowCounter(19L, 0, null);

        service.beginAttempt(16L);
        service.beginAttempt(19L);

        assertEquals(4, first.getFailedAttempts());
        assertEquals(1, second.getFailedAttempts());
        assertNull(first.getLockedUntil());
        assertNull(second.getLockedUntil());
    }

    @Test
    void invalidAccountIdsAreRejectedBeforeRepositoryAccess() {
        assertThrows(IllegalArgumentException.class,
                () -> service.beginAttempt(null));
        assertThrows(IllegalArgumentException.class,
                () -> service.beginAttempt(0L));
        assertThrows(IllegalArgumentException.class,
                () -> service.beginAttempt(-1L));
        assertThrows(IllegalArgumentException.class,
                () -> service.recordSuccess(null));
        assertThrows(IllegalArgumentException.class,
                () -> service.recordSuccess(0L));

        verifyNoInteractions(repository);
    }

    @Test
    void missingCounterAfterInitializationIsReported() {
        assertThrows(IllegalStateException.class,
                () -> service.beginAttempt(16L));

        verify(repository).initializeIfMissing(16L);
        verify(repository).findByUserIdForUpdate(16L);
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void reducedConfiguredLimitBlocksExistingHigherCount() {
        AccessVerificationAttempt attempt = allowCounter(16L, 4, null);
        var stricterService =
                new AccessVerificationAttemptService(repository, 3, 60);

        var decision = stricterService.beginAttempt(16L);

        assertFalse(decision.allowed());
        assertEquals(4, attempt.getFailedAttempts());
        assertNotNull(attempt.getLockedUntil());
        verify(repository).saveAndFlush(attempt);
    }

    @Test
    void customAttemptLimitAndLockDurationAreUsed() {
        AccessVerificationAttempt attempt = allowCounter(16L, 2, null);
        var customService =
                new AccessVerificationAttemptService(repository, 3, 120);

        LocalDateTime before = LocalDateTime.now();
        var decision = customService.beginAttempt(16L);
        LocalDateTime after = LocalDateTime.now();

        assertTrue(decision.allowed());
        assertEquals(0, decision.remainingAttempts());
        assertEquals(3, attempt.getFailedAttempts());
        assertNotNull(decision.lockedUntil());
        assertFalse(decision.lockedUntil().isBefore(
                before.plusSeconds(120)
        ));
        assertFalse(decision.lockedUntil().isAfter(
                after.plusSeconds(120)
        ));
    }

    @Test
    void invalidConfigurationIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> new AccessVerificationAttemptService(
                        repository, 0, 60
                ));
        assertThrows(IllegalArgumentException.class,
                () -> new AccessVerificationAttemptService(
                        repository, -1, 60
                ));
        assertThrows(IllegalArgumentException.class,
                () -> new AccessVerificationAttemptService(
                        repository, 5, 0
                ));
        assertThrows(IllegalArgumentException.class,
                () -> new AccessVerificationAttemptService(
                        repository, 5, -1
                ));

        verifyNoInteractions(repository);
    }

    @Test
    void successCannotSilentlyIgnoreMissingCounter() {
        assertThrows(IllegalStateException.class,
                () -> service.recordSuccess(16L));

        verify(repository, never()).saveAndFlush(any());
    }
}