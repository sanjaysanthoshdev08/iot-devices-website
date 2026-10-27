package com.hykon.iot_backend.service;

import com.hykon.iot_backend.dto.ClaimDeviceRequest;
import com.hykon.iot_backend.dto.DeviceResponse;
import com.hykon.iot_backend.dto.DistributorAccessRequest;
import com.hykon.iot_backend.dto.DistributorAccessResponse;
import com.hykon.iot_backend.entity.RoleName;
import com.hykon.iot_backend.entity.User;
import com.hykon.iot_backend.repository.UserRepository;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@Service
@Transactional(propagation = Propagation.NOT_SUPPORTED)
public class AccessVerificationCoordinator {

    private final UserRepository userRepository;
    private final LoginSessionService sessionService;
    private final AccessVerificationAttemptService attemptService;
    private final SessionAccessVerificationService verificationService;

    public AccessVerificationCoordinator(
            UserRepository userRepository,
            LoginSessionService sessionService,
            AccessVerificationAttemptService attemptService,
            SessionAccessVerificationService verificationService
    ) {
        this.userRepository = userRepository;
        this.sessionService = sessionService;
        this.attemptService = attemptService;
        this.verificationService = verificationService;
    }

    public DeviceResponse verifyDevice(
            ClaimDeviceRequest request,
            String email,
            UUID sessionId
    ) {
        User account = requireAccount(
                email, sessionId, RoleName.USER
        );

        requireAttemptAllowed(account.getId());

        DeviceResponse response = verificationService.verifyDevice(
                request, email, sessionId
        );

        attemptService.recordSuccess(account.getId());

        return response;
    }

    public DistributorAccessResponse verifyDistributor(
            DistributorAccessRequest request,
            String email,
            UUID sessionId
    ) {
        User account = requireAccount(
                email, sessionId, RoleName.DISTRIBUTOR_ADMIN
        );

        requireAttemptAllowed(account.getId());

        DistributorAccessResponse response =
                verificationService.verifyDistributor(
                        request, email, sessionId
                );

        attemptService.recordSuccess(account.getId());

        return response;
    }

    private User requireAccount(
            String email,
            UUID sessionId,
            RoleName requiredRole
    ) {
        if (email == null || email.isBlank()) {
            throw forbidden();
        }

        User account = userRepository.findByEmailWithRole(email)
                .orElseThrow(this::forbidden);

        if (!"ACTIVE".equalsIgnoreCase(account.getStatus())
                || account.getId() == null
                || account.getRole() == null
                || account.getRole().getName() != requiredRole) {
            throw forbidden();
        }

        sessionService.requireActive(sessionId, account.getId());

        return account;
    }

    private void requireAttemptAllowed(Long userId) {
        AccessVerificationAttemptService.AttemptDecision decision =
                attemptService.beginAttempt(userId);

        if (!decision.allowed()) {
            throw new ResponseStatusException(
                    HttpStatus.TOO_MANY_REQUESTS,
                    "Too many verification attempts. Try again later."
            );
        }
    }

    private ResponseStatusException forbidden() {
        return new ResponseStatusException(
                HttpStatus.FORBIDDEN,
                "You do not have permission to verify this access"
        );
    }
}