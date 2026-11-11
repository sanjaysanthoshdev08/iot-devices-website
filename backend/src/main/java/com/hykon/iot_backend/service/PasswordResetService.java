package com.hykon.iot_backend.service;

import com.hykon.iot_backend.entity.PasswordResetToken;
import com.hykon.iot_backend.entity.User;
import com.hykon.iot_backend.repository.PasswordResetTokenRepository;
import com.hykon.iot_backend.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;

@Service
public class PasswordResetService {

    private static final Logger log = LoggerFactory.getLogger(PasswordResetService.class);
    private static final long CODE_MINUTES = 15;
    private static final int MAX_ATTEMPTS = 5;

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository tokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final PasswordResetEmailSender emailSender;
    private final SecureRandom random = new SecureRandom();

    public PasswordResetService(
            UserRepository userRepository,
            PasswordResetTokenRepository tokenRepository,
            PasswordEncoder passwordEncoder,
            PasswordResetEmailSender emailSender
    ) {
        this.userRepository = userRepository;
        this.tokenRepository = tokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.emailSender = emailSender;
    }

    /**
     * Always returns normally so callers cannot learn whether an email is registered.
     */
    @Transactional
    public void requestReset(String email) {
        User user = findActiveUser(email);
        if (user == null) {
            return;
        }

        // Only the newest code is valid.
        tokenRepository.invalidateAllForUser(user.getId());

        String code = String.format("%06d", random.nextInt(1_000_000));

        PasswordResetToken entity = new PasswordResetToken();
        entity.setUser(user);
        entity.setCodeHash(hash(code));
        entity.setExpiresAt(LocalDateTime.now().plusMinutes(CODE_MINUTES));
        tokenRepository.save(entity);

        emailSender.sendResetCode(user.getEmail(), code, CODE_MINUTES);
    }

    @Transactional(noRollbackFor = IllegalArgumentException.class)
    public void resetPassword(String email, String code, String newPassword, String confirmPassword) {
        if (newPassword == null || !newPassword.equals(confirmPassword)) {
            throw new IllegalArgumentException("Passwords do not match");
        }

        User user = findActiveUser(email);
        if (user == null) {
            throw invalidCode();
        }

        List<PasswordResetToken> active = tokenRepository.findActiveForUserForUpdate(user.getId());
        if (active.isEmpty()) {
            throw invalidCode();
        }
        PasswordResetToken entity = active.get(0);

        if (!entity.getExpiresAt().isAfter(LocalDateTime.now())
                || entity.getAttempts() >= MAX_ATTEMPTS) {
            entity.setUsed(true);
            tokenRepository.save(entity);
            throw invalidCode();
        }

        if (!MessageDigest.isEqual(
                hash(code == null ? "" : code).getBytes(StandardCharsets.UTF_8),
                entity.getCodeHash().getBytes(StandardCharsets.UTF_8))) {
            entity.setAttempts(entity.getAttempts() + 1);
            if (entity.getAttempts() >= MAX_ATTEMPTS) {
                entity.setUsed(true);
            }
            tokenRepository.save(entity);
            throw new IllegalArgumentException("The reset code is incorrect.");
        }

        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        tokenRepository.invalidateAllForUser(user.getId());
        tokenRepository.revokeLoginSessionsForUser(user.getId());
        tokenRepository.revokeRefreshTokensForUser(user.getId());
    }

    private User findActiveUser(String email) {
        if (email == null || email.isBlank()) {
            return null;
        }
        User user = userRepository.findByEmailWithRole(email.trim()).orElse(null);
        if (user == null || !"ACTIVE".equalsIgnoreCase(user.getStatus())) {
            return null;
        }
        return user;
    }

    private static IllegalArgumentException invalidCode() {
        return new IllegalArgumentException(
                "This reset code is invalid or has expired. Please request a new one.");
    }

    private static String hash(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
