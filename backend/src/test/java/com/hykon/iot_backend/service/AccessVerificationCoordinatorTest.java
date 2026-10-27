package com.hykon.iot_backend.service;

import com.hykon.iot_backend.dto.*;
import com.hykon.iot_backend.entity.*;
import com.hykon.iot_backend.exception.InvalidTokenException;
import com.hykon.iot_backend.repository.UserRepository;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AccessVerificationCoordinatorTest {

    private static final String EMAIL = "account@example.com";
    private static final UUID SESSION_ID = UUID.randomUUID();

    @Mock
    private UserRepository userRepository;

    @Mock
    private LoginSessionService sessionService;

    @Mock
    private AccessVerificationAttemptService attemptService;

    @Mock
    private SessionAccessVerificationService verificationService;

    @InjectMocks
    private AccessVerificationCoordinator coordinator;

    private User allowAccount(Long id, RoleName role) {
        User user = new User();
        ReflectionTestUtils.setField(user, "id", id);
        user.setStatus("ACTIVE");
        user.setRole(new Role(role));

        when(userRepository.findByEmailWithRole(EMAIL))
                .thenReturn(Optional.of(user));

        return user;
    }

    private AccessVerificationAttemptService.AttemptDecision allowed() {
        return new AccessVerificationAttemptService.AttemptDecision(
                true, 4, null
        );
    }

    private AccessVerificationAttemptService.AttemptDecision blocked() {
        return new AccessVerificationAttemptService.AttemptDecision(
                false, 0, LocalDateTime.now().plusMinutes(1)
        );
    }

    private void assertStatus(HttpStatus status, Runnable action) {
        ResponseStatusException error = assertThrows(
                ResponseStatusException.class, action::run
        );
        assertEquals(status, error.getStatusCode());
    }

    @Test
    void deviceSuccessResetsCounterAfterVerificationReturns() {
        allowAccount(16L, RoleName.USER);
        ClaimDeviceRequest request = new ClaimDeviceRequest();

        DeviceResponse response = new DeviceResponse(
                1L, "HALO1200-1", "SN-001", "HALO 1200",
                null, 1L, 1L, 16L, "ACTIVE", null
        );

        when(attemptService.beginAttempt(16L)).thenReturn(allowed());
        when(verificationService.verifyDevice(request, EMAIL, SESSION_ID))
                .thenReturn(response);

        assertSame(response,
                coordinator.verifyDevice(request, EMAIL, SESSION_ID));

        var order = inOrder(
                sessionService, attemptService, verificationService
        );
        order.verify(sessionService).requireActive(SESSION_ID, 16L);
        order.verify(attemptService).beginAttempt(16L);
        order.verify(verificationService)
                .verifyDevice(request, EMAIL, SESSION_ID);
        order.verify(attemptService).recordSuccess(16L);
    }

    @Test
    void distributorSuccessResetsCounterAfterVerificationReturns() {
        allowAccount(22L, RoleName.DISTRIBUTOR_ADMIN);
        DistributorAccessRequest request = new DistributorAccessRequest();

        DistributorAccessResponse response =
                new DistributorAccessResponse(
                        1L, "DIST-A", "DEV",
                        1L, "MAIN", "ERNAKULAM", "EKM"
                );

        when(attemptService.beginAttempt(22L)).thenReturn(allowed());
        when(verificationService.verifyDistributor(
                request, EMAIL, SESSION_ID
        )).thenReturn(response);

        assertSame(response,
                coordinator.verifyDistributor(request, EMAIL, SESSION_ID));

        var order = inOrder(
                sessionService, attemptService, verificationService
        );
        order.verify(sessionService).requireActive(SESSION_ID, 22L);
        order.verify(attemptService).beginAttempt(22L);
        order.verify(verificationService)
                .verifyDistributor(request, EMAIL, SESSION_ID);
        order.verify(attemptService).recordSuccess(22L);
    }

    @Test
    void lockedCustomerCannotReachVerification() {
        allowAccount(16L, RoleName.USER);
        when(attemptService.beginAttempt(16L)).thenReturn(blocked());

        assertStatus(HttpStatus.TOO_MANY_REQUESTS,
                () -> coordinator.verifyDevice(
                        new ClaimDeviceRequest(), EMAIL, SESSION_ID
                ));

        verifyNoInteractions(verificationService);
        verify(attemptService, never()).recordSuccess(any());
    }

    @Test
    void lockedDistributorCannotReachVerification() {
        allowAccount(22L, RoleName.DISTRIBUTOR_ADMIN);
        when(attemptService.beginAttempt(22L)).thenReturn(blocked());

        assertStatus(HttpStatus.TOO_MANY_REQUESTS,
                () -> coordinator.verifyDistributor(
                        new DistributorAccessRequest(), EMAIL, SESSION_ID
                ));

        verifyNoInteractions(verificationService);
        verify(attemptService, never()).recordSuccess(any());
    }

    @Test
    void failedDeviceVerificationDoesNotResetCounter() {
        allowAccount(16L, RoleName.USER);
        ClaimDeviceRequest request = new ClaimDeviceRequest();

        when(attemptService.beginAttempt(16L)).thenReturn(allowed());
        when(verificationService.verifyDevice(request, EMAIL, SESSION_ID))
                .thenThrow(new IllegalArgumentException(
                        "Device verification failed"
                ));

        assertThrows(IllegalArgumentException.class,
                () -> coordinator.verifyDevice(request, EMAIL, SESSION_ID));

        verify(attemptService).beginAttempt(16L);
        verify(attemptService, never()).recordSuccess(any());
    }

    @Test
    void failedDistributorVerificationDoesNotResetCounter() {
        allowAccount(22L, RoleName.DISTRIBUTOR_ADMIN);
        DistributorAccessRequest request = new DistributorAccessRequest();

        when(attemptService.beginAttempt(22L)).thenReturn(allowed());
        when(verificationService.verifyDistributor(
                request, EMAIL, SESSION_ID
        )).thenThrow(new IllegalArgumentException(
                "Distributor verification failed"
        ));

        assertThrows(IllegalArgumentException.class,
                () -> coordinator.verifyDistributor(
                        request, EMAIL, SESSION_ID
                ));

        verify(attemptService, never()).recordSuccess(any());
    }

    @Test
    void invalidSessionDoesNotConsumeAnAttempt() {
        allowAccount(16L, RoleName.USER);
        when(sessionService.requireActive(SESSION_ID, 16L))
                .thenThrow(new InvalidTokenException("Invalid session"));

        assertThrows(InvalidTokenException.class,
                () -> coordinator.verifyDevice(
                        new ClaimDeviceRequest(), EMAIL, SESSION_ID
                ));

        verifyNoInteractions(attemptService, verificationService);
    }

    @Test
    void companyAdminCannotUseEitherVerificationGate() {
        allowAccount(5L, RoleName.HYKON_ADMIN);

        assertStatus(HttpStatus.FORBIDDEN,
                () -> coordinator.verifyDevice(
                        new ClaimDeviceRequest(), EMAIL, SESSION_ID
                ));

        assertStatus(HttpStatus.FORBIDDEN,
                () -> coordinator.verifyDistributor(
                        new DistributorAccessRequest(), EMAIL, SESSION_ID
                ));

        verifyNoInteractions(
                sessionService, attemptService, verificationService
        );
    }

    @Test
    void inactiveAccountCannotConsumeAttempts() {
        User user = allowAccount(16L, RoleName.USER);
        user.setStatus("INACTIVE");

        assertStatus(HttpStatus.FORBIDDEN,
                () -> coordinator.verifyDevice(
                        new ClaimDeviceRequest(), EMAIL, SESSION_ID
                ));

        verifyNoInteractions(
                sessionService, attemptService, verificationService
        );
    }

    @Test
    void missingIdentityAndUnknownAccountAreForbidden() {
        assertStatus(HttpStatus.FORBIDDEN,
                () -> coordinator.verifyDevice(
                        new ClaimDeviceRequest(), null, SESSION_ID
                ));

        assertStatus(HttpStatus.FORBIDDEN,
                () -> coordinator.verifyDevice(
                        new ClaimDeviceRequest(), EMAIL, SESSION_ID
                ));

        verifyNoInteractions(
                sessionService, attemptService, verificationService
        );
    }

    @Test
    void anotherLoginUsesTheSameAccountAttemptCounter() {
        allowAccount(16L, RoleName.USER);
        ClaimDeviceRequest request = new ClaimDeviceRequest();
        UUID secondSession = UUID.randomUUID();

        when(attemptService.beginAttempt(16L))
                .thenReturn(allowed(), blocked());
        when(verificationService.verifyDevice(request, EMAIL, SESSION_ID))
                .thenThrow(new IllegalArgumentException(
                        "Device verification failed"
                ));

        assertThrows(IllegalArgumentException.class,
                () -> coordinator.verifyDevice(request, EMAIL, SESSION_ID));

        assertStatus(HttpStatus.TOO_MANY_REQUESTS,
                () -> coordinator.verifyDevice(
                        request, EMAIL, secondSession
                ));

        verify(sessionService).requireActive(SESSION_ID, 16L);
        verify(sessionService).requireActive(secondSession, 16L);
        verify(attemptService, times(2)).beginAttempt(16L);
        verify(verificationService, never())
                .verifyDevice(request, EMAIL, secondSession);
        verify(attemptService, never()).recordSuccess(any());
    }

    @Test
    void storageFailureDoesNotResetCounterAsSuccess() {
        allowAccount(16L, RoleName.USER);
        ClaimDeviceRequest request = new ClaimDeviceRequest();

        when(attemptService.beginAttempt(16L)).thenReturn(allowed());
        when(verificationService.verifyDevice(request, EMAIL, SESSION_ID))
                .thenThrow(new DataAccessResourceFailureException(
                        "Test storage failure"
                ));

        assertThrows(DataAccessResourceFailureException.class,
                () -> coordinator.verifyDevice(request, EMAIL, SESSION_ID));

        verify(attemptService, never()).recordSuccess(any());
    }
}