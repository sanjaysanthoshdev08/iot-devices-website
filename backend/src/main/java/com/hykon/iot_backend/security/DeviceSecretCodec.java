package com.hykon.iot_backend.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.regex.Pattern;

/**
 * Generates high-entropy device secrets, not website passwords.
 * Store only the SHA-256 hash; never log the plaintext secret.
 */
public final class DeviceSecretCodec {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Pattern SECRET =
            Pattern.compile("[A-Za-z0-9_-]{43}");
    private static final Pattern HASH =
            Pattern.compile("[0-9a-f]{64}");

    private DeviceSecretCodec() {}

    public static String generate() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public static String hash(String secret) {
        if (!validSecret(secret)) {
            throw new IllegalArgumentException("Invalid device secret format");
        }

        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(
                    secret.getBytes(StandardCharsets.UTF_8)
            );
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    public static boolean matches(String suppliedSecret, String storedHash) {
        if (!validSecret(suppliedSecret)
                || storedHash == null
                || !HASH.matcher(storedHash).matches()) {
            return false;
        }

        return MessageDigest.isEqual(
                HexFormat.of().parseHex(hash(suppliedSecret)),
                HexFormat.of().parseHex(storedHash)
        );
    }

    private static boolean validSecret(String secret) {
        if (secret == null || !SECRET.matcher(secret).matches()) {
            return false;
        }

        try {
            byte[] decoded = Base64.getUrlDecoder().decode(secret);
            return decoded.length == 32
                    && Base64.getUrlEncoder().withoutPadding()
                            .encodeToString(decoded).equals(secret);
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }
}