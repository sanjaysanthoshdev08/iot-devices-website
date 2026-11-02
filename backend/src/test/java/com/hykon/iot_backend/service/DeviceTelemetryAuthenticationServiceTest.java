package com.hykon.iot_backend.service;

import com.hykon.iot_backend.repository.DeviceTelemetryCredentialStore;
import com.hykon.iot_backend.repository.DeviceTelemetryCredentialStore.StoredCredential;
import com.hykon.iot_backend.security.DeviceSecretCodec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DeviceTelemetryAuthenticationServiceTest {

    private DeviceTelemetryCredentialStore store;
    private DeviceTelemetryAuthenticationService service;
    private UUID keyId;
    private String secret;

    @BeforeEach
    void setUp() {
        store = mock(DeviceTelemetryCredentialStore.class);
        service = new DeviceTelemetryAuthenticationService(store);
        keyId = UUID.randomUUID();
        secret = DeviceSecretCodec.generate();
    }

    private StoredCredential credential(Instant revokedAt) {
        return new StoredCredential(
                keyId, 101L, DeviceSecretCodec.hash(secret), revokedAt);
    }

    private void rejects(String key, String suppliedSecret) {
        var error = assertThrows(
                ResponseStatusException.class,
                () -> service.authenticate(key, suppliedSecret)
        );
        assertEquals(HttpStatus.UNAUTHORIZED, error.getStatusCode());
        assertEquals("Invalid device credential", error.getReason());
        assertFalse(error.getMessage().contains(secret));
        assertFalse(error.getMessage().contains(keyId.toString()));
    }

    @Test
    void acceptsActiveMatchingCredentialWithoutRetainingSecret() {
        when(store.findByKeyId(keyId))
                .thenReturn(Optional.of(credential(null)));

        var identity = service.authenticate(keyId.toString(), secret);

        assertEquals(keyId, identity.keyId());
        assertEquals(101L, identity.deviceId());
        assertEquals("DeviceIdentity[redacted]", identity.toString());
        verify(store).findByKeyId(keyId);
        verifyNoMoreInteractions(store);
    }

    @Test
    void rejectsMissingMalformedAndNoncanonicalKeyBeforeLookup() {
        for (String key : new String[] {
                null, "", "invalid", "1-1-1-1-1",
                " " + keyId, keyId + " "
        }) {
            rejects(key, secret);
        }
        verifyNoInteractions(store);
    }

    @Test
    void rejectsMissingOrWrongLengthSecretBeforeLookup() {
        for (String supplied : new String[] {
                null, "", "short", secret + " ", "Bearer " + secret
        }) {
            rejects(keyId.toString(), supplied);
        }
        verifyNoInteractions(store);
    }

    @Test
    void rejectsUnknownCredential() {
        when(store.findByKeyId(keyId)).thenReturn(Optional.empty());

        rejects(keyId.toString(), secret);
    }

    @Test
    void rejectsRevokedCredentialEvenWithCorrectSecret() {
        when(store.findByKeyId(keyId))
                .thenReturn(Optional.of(credential(Instant.now())));

        rejects(keyId.toString(), secret);
    }

    @Test
    void rejectsWrongAndMalformedSecrets() {
        when(store.findByKeyId(keyId))
                .thenReturn(Optional.of(credential(null)));

        rejects(keyId.toString(), DeviceSecretCodec.generate());
        rejects(keyId.toString(), "!".repeat(43));
        rejects(keyId.toString(), "A".repeat(42) + "B");
    }

    @Test
    void rejectsInvalidStoredHashOrIdentity() {
        for (StoredCredential invalid : new StoredCredential[] {
                new StoredCredential(keyId, 101L, "invalid", null),
                new StoredCredential(keyId, 0L,
                        DeviceSecretCodec.hash(secret), null),
                new StoredCredential(UUID.randomUUID(), 101L,
                        DeviceSecretCodec.hash(secret), null)
        }) {
            when(store.findByKeyId(keyId))
                    .thenReturn(Optional.of(invalid));
            rejects(keyId.toString(), secret);
        }
    }

    @Test
    void propagatesDatabaseFailureInsteadOfMisreportingBadCredentials() {
        var failure = new DataAccessResourceFailureException(
                "Test database unavailable");
        when(store.findByKeyId(keyId)).thenThrow(failure);

        var actual = assertThrows(
                DataAccessResourceFailureException.class,
                () -> service.authenticate(keyId.toString(), secret)
        );
        assertSame(failure, actual);
    }
}