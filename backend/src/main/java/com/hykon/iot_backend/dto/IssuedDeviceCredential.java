package com.hykon.iot_backend.dto;

import java.util.UUID;

/**
 * The plaintext secret is returned only when a credential is issued.
 * Never log this object or persist its secret.
 */
public record IssuedDeviceCredential(
        UUID keyId,
        Long deviceId,
        String deviceCode,
        String secret
) {
    @Override
    public String toString() {
        return "IssuedDeviceCredential[redacted]";
    }
}