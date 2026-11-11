package com.hykon.iot_backend.service;

import com.hykon.iot_backend.entity.PasswordResetToken;
import com.hykon.iot_backend.entity.User;
import com.hykon.iot_backend.repository.PasswordResetTokenRepository;
import com.hykon.iot_backend.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;

@Service
public class PasswordResetService {

    private static final Logger log = LoggerFactory.getLogger(PasswordResetService.class);
    private static final long TOKEN_MINUTES = 30;

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository tokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final String frontendUrl;
    private final SecureRandom random = new SecureRandom();

    public PasswordResetService(
            UserRepository userRepository,
            PasswordResetTokenRepository tokenRepository,
            PasswordEncoder passwordEncoder,
            @Value("${app.frontend-url:http://localhost:4200}") String frontendUrl
    ) {
        this.userRepository = userRepository;
        this.tokenRepository = tokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.frontendUrl = frontendUrl;
    }

    /**
     * Always returns normally so callers cannot learn whether an email is registered.
     */
    @Transactional
    public void requestReset(String email) {
        if (email == null) {
            return;
        }

        User user = userRepository.findByEmailWithRole(email.trim()).orElse(null);
        if (user == null || !"ACTIVE".equalsIgnoreCase(user.getStatus())) {
            return;
        }

        tokenRepository.invalidateAllForUser(user.getId());

        byte[] raw = new byte[32];
        random.nextBytes(raw);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);

        PasswordResetToken entity = new PasswordResetToken();
        entity.setUser(user);
        entity.setTokenHash(hash(token));
        entity.setExpiresAt(LocalDateTime.now().plusMinutes(TOKEN_MINUTES));
        tokenRepository.save(entity);

        // No email provider is configured yet: the link is logged for local
        // development. Replace with a real mail sender before production.
        log.info("DEV ONLY - password reset link for {}: {}/reset-password?token={}",
                user.getEmail(), frontendUrl, token);
    }

    @Transactional
    public void resetPassword(String token, String newPassword, String confirmPassword) {
        if (newPassword == null || !newPassword.equals(confirmPassword)) {
            throw new IllegalArgumentException("Passwords do not match");
        }

        PasswordResetToken entity = tokenRepository
                .findByTokenHashForUpdate(hash(token == null ? "" : token))
                .orElseThrow(PasswordResetService::invalidLink);

        if (entity.isUsed() || !entity.getExpiresAt().isAfter(LocalDateTime.now())) {
            throw invalidLink();
        }

        User user = entity.getUser();
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        tokenRepository.invalidateAllForUser(user.getId());
        tokenRepository.revokeLoginSessionsForUser(user.getId());
        tokenRepository.revokeRefreshTokensForUser(user.getId());
    }

    private static IllegalArgumentException invalidLink() {
        return new IllegalArgumentException(
                "This reset link is invalid or has expired. Please request a new one.");
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
