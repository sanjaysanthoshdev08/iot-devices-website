package com.hykon.iot_backend.service;

import com.hykon.iot_backend.repository.DeviceTelemetryCredentialStore;
import com.hykon.iot_backend.security.DeviceSecretCodec;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class DeviceTelemetryAuthenticationService {

    private final DeviceTelemetryCredentialStore credentials;

    public DeviceTelemetryAuthenticationService(
            DeviceTelemetryCredentialStore credentials
    ) {
        this.credentials = credentials;
    }

    /**
     * Checks the supplied device credential without retaining its secret.
     * Ingestion must also check device state and recheck credential revocation
     * inside its transaction after acquiring the device row lock.
     */
    public DeviceIdentity authenticate(String suppliedKeyId, String secret) {
        UUID keyId = parseKeyId(suppliedKeyId);

        if (secret == null || secret.length() != 43) {
            throw unauthorized();
        }

        var credential = credentials.findByKeyId(keyId)
                .orElseThrow(DeviceTelemetryAuthenticationService::unauthorized);

        if (!keyId.equals(credential.keyId())
                || credential.deviceId() <= 0
                || credential.revokedAt() != null
                || !DeviceSecretCodec.matches(secret, credential.secretHash())) {
            throw unauthorized();
        }

        return new DeviceIdentity(keyId, credential.deviceId());
    }

    private static UUID parseKeyId(String supplied) {
        if (supplied == null || supplied.length() != 36) {
            throw unauthorized();
        }

        final UUID parsed;
        try {
            parsed = UUID.fromString(supplied);
        } catch (IllegalArgumentException exception) {
            throw unauthorized();
        }

        if (!parsed.toString().equalsIgnoreCase(supplied)) {
            throw unauthorized();
        }
        return parsed;
    }

    private static ResponseStatusException unauthorized() {
        return new ResponseStatusException(
                HttpStatus.UNAUTHORIZED, "Invalid device credential");
    }

    public record DeviceIdentity(UUID keyId, long deviceId) {
        @Override
        public String toString() {
            return "DeviceIdentity[redacted]";
        }
    }
}