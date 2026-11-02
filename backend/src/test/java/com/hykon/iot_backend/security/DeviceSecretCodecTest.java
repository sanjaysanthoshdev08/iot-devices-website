package com.hykon.iot_backend.security;

import org.junit.jupiter.api.Test;

import java.util.Base64;
import java.util.HashSet;

import static org.junit.jupiter.api.Assertions.*;

class DeviceSecretCodecTest {

    @Test
    void generatesCanonical256BitSecrets() {
        String secret = DeviceSecretCodec.generate();

        assertTrue(secret.matches("[A-Za-z0-9_-]{43}"));
        assertEquals(32, Base64.getUrlDecoder().decode(secret).length);
    }

    @Test
    void successiveSecretsDiffer() {
        var secrets = new HashSet<String>();
        for (int i = 0; i < 100; i++) {
            assertTrue(secrets.add(DeviceSecretCodec.generate()));
        }
    }

    @Test
    void hashHasDatabaseCompatibleFormatAndMatchesOriginal() {
        String secret = DeviceSecretCodec.generate();
        String hash = DeviceSecretCodec.hash(secret);

        assertTrue(hash.matches("[0-9a-f]{64}"));
        assertNotEquals(secret, hash);
        assertEquals(hash, DeviceSecretCodec.hash(secret));
        assertTrue(DeviceSecretCodec.matches(secret, hash));
    }

    @Test
    void wrongSecretDoesNotAuthenticate() {
        String secret = DeviceSecretCodec.generate();
        String other = secret.charAt(0) == 'A'
                ? "B" + secret.substring(1)
                : "A" + secret.substring(1);

        assertFalse(DeviceSecretCodec.matches(
                other, DeviceSecretCodec.hash(secret)));
    }

    @Test
    void malformedSecretsAreRejectedWithoutExposingTheirContent() {
        for (String secret : new String[]{
                "", "short", " ".repeat(43), "!".repeat(43),
                "A".repeat(44), "A".repeat(42) + "B"
        }) {
            var error = assertThrows(
                    IllegalArgumentException.class,
                    () -> DeviceSecretCodec.hash(secret)
            );
            assertEquals("Invalid device secret format", error.getMessage());
            assertFalse(DeviceSecretCodec.matches(secret, "a".repeat(64)));
        }

        assertFalse(DeviceSecretCodec.matches(null, "a".repeat(64)));
        assertThrows(IllegalArgumentException.class,
                () -> DeviceSecretCodec.hash(null));
    }

    @Test
    void malformedStoredHashesFailClosed() {
        String secret = DeviceSecretCodec.generate();

        for (String hash : new String[]{
                "", "a".repeat(63), "a".repeat(65),
                "G".repeat(64), " ".repeat(64)
        }) {
            assertFalse(DeviceSecretCodec.matches(secret, hash));
        }

        assertFalse(DeviceSecretCodec.matches(secret, null));
    }

    @Test
    void doesNotTrimOrAcceptPrefixedSecrets() {
        String secret = DeviceSecretCodec.generate();
        String hash = DeviceSecretCodec.hash(secret);

        assertFalse(DeviceSecretCodec.matches(" " + secret, hash));
        assertFalse(DeviceSecretCodec.matches(secret + " ", hash));
        assertFalse(DeviceSecretCodec.matches("Bearer " + secret, hash));
    }
}