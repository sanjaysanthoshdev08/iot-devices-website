package com.hykon.iot_backend.service;

import com.hykon.iot_backend.entity.AccessVerificationAttempt;
import com.hykon.iot_backend.repository.AccessVerificationAttemptRepository;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class AccessVerificationAttemptService {

    private final AccessVerificationAttemptRepository attemptRepository;
    private final int maxAttempts;
    private final long lockoutSeconds;

    public AccessVerificationAttemptService(
            AccessVerificationAttemptRepository attemptRepository,
            @Value("${access.verification.max-attempts:5}") int maxAttempts,
            @Value("${access.verification.lockout-seconds:60}") long lockoutSeconds
    ) {
        if (maxAttempts <= 0 || lockoutSeconds <= 0) {
            throw new IllegalArgumentException(
                    "Attempt limit and lockout duration must be greater than zero"
            );
        }

        this.attemptRepository = attemptRepository;
        this.maxAttempts = maxAttempts;
        this.lockoutSeconds = lockoutSeconds;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public AttemptDecision beginAttempt(Long userId) {
        requireUserId(userId);

        attemptRepository.initializeIfMissing(userId);

        AccessVerificationAttempt attempt = attemptRepository
                .findByUserIdForUpdate(userId)
                .orElseThrow(() -> new IllegalStateException(
                        "Verification attempt record is missing"
                ));

        LocalDateTime now = LocalDateTime.now();

        if (attempt.getLockedUntil() != null) {
            if (attempt.getLockedUntil().isAfter(now)) {
                return new AttemptDecision(
                        false, 0, attempt.getLockedUntil()
                );
            }

            // An expired lock starts a fresh allowance.
            attempt.setFailedAttempts(0);
            attempt.setLockedUntil(null);
        }

        // Handle a reduced configuration limit safely.
        if (attempt.getFailedAttempts() >= maxAttempts) {
            attempt.setLockedUntil(now.plusSeconds(lockoutSeconds));
            attemptRepository.saveAndFlush(attempt);

            return new AttemptDecision(
                    false, 0, attempt.getLockedUntil()
            );
        }

        // Reserve the attempt before running verification.
        attempt.setFailedAttempts(attempt.getFailedAttempts() + 1);

        if (attempt.getFailedAttempts() >= maxAttempts) {
            attempt.setLockedUntil(now.plusSeconds(lockoutSeconds));
        }

        attemptRepository.saveAndFlush(attempt);

        return new AttemptDecision(
                true,
                maxAttempts - attempt.getFailedAttempts(),
                attempt.getLockedUntil()
        );
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordSuccess(Long userId) {
        requireUserId(userId);

        AccessVerificationAttempt attempt = attemptRepository
                .findByUserIdForUpdate(userId)
                .orElseThrow(() -> new IllegalStateException(
                        "Verification attempt record is missing"
                ));

        attempt.setFailedAttempts(0);
        attempt.setLockedUntil(null);
        attemptRepository.saveAndFlush(attempt);
    }

    private void requireUserId(Long userId) {
        if (userId == null || userId <= 0) {
            throw new IllegalArgumentException(
                    "A valid account ID is required"
            );
        }
    }

    public record AttemptDecision(
            boolean allowed,
            int remainingAttempts,
            LocalDateTime lockedUntil
    ) {
    }
}