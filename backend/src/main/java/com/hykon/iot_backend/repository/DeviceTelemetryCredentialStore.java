package com.hykon.iot_backend.repository;

import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public class DeviceTelemetryCredentialStore {

    private final JdbcTemplate jdbc;

    public DeviceTelemetryCredentialStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record StoredCredential(
            UUID keyId,
            long deviceId,
            String secretHash,
            Instant revokedAt
    ) {
        @Override
        public String toString() {
            return "StoredCredential[redacted]";
        }
    }

    public void insert(
            UUID keyId,
            long deviceId,
            String secretHash,
            long createdByUserId
    ) {
        if (keyId == null || deviceId <= 0 || createdByUserId <= 0
                || secretHash == null
                || !secretHash.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException(
                    "Invalid device credential metadata");
        }

        jdbc.update("""
                INSERT INTO device_telemetry_credentials
                    (key_id, device_id, secret_hash, created_by_user_id)
                VALUES (?, ?, ?, ?)
                """, keyId, deviceId, secretHash, createdByUserId);
    }

    public Optional<StoredCredential> findByKeyId(UUID keyId) {
        if (keyId == null) {
            return Optional.empty();
        }

        try {
            return Optional.ofNullable(jdbc.queryForObject("""
                    SELECT key_id, device_id, secret_hash, revoked_at
                    FROM device_telemetry_credentials
                    WHERE key_id = ?
                    """, (result, row) -> {
                var revoked = result.getTimestamp("revoked_at");
                return new StoredCredential(
                        result.getObject("key_id", UUID.class),
                        result.getLong("device_id"),
                        result.getString("secret_hash"),
                        revoked == null ? null : revoked.toInstant()
                );
            }, keyId));
        } catch (EmptyResultDataAccessException exception) {
            return Optional.empty();
        }
    }

    /**
     * Call inside the issuance/revocation service transaction,
     * after locking the corresponding device row.
     */
    public int revokeActiveForDevice(long deviceId) {
        if (deviceId <= 0) {
            throw new IllegalArgumentException(
                    "Device ID must be positive");
        }

        return jdbc.update("""
                UPDATE device_telemetry_credentials
                SET revoked_at = CURRENT_TIMESTAMP
                WHERE device_id = ? AND revoked_at IS NULL
                """, deviceId);
    }
}