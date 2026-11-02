package com.hykon.iot_backend.service;

import com.hykon.iot_backend.dto.TelemetryRequest;
import com.hykon.iot_backend.repository.DeviceRepository;
import com.hykon.iot_backend.repository.DeviceTelemetryStore;
import jakarta.validation.Validator;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Service
@Transactional
public class DeviceTelemetryIngestionService {

    private final DeviceTelemetryAuthenticationService authentication;
    private final DeviceRepository devices;
    private final DeviceTelemetryStore store;
    private final Validator validator;

    public DeviceTelemetryIngestionService(
            DeviceTelemetryAuthenticationService authentication,
            DeviceRepository devices,
            DeviceTelemetryStore store,
            Validator validator
    ) {
        this.authentication = authentication;
        this.devices = devices;
        this.store = store;
        this.validator = validator;
    }

    public Receipt ingest(
            String keyId, String secret, TelemetryRequest request
    ) {
        var identity = authentication.authenticate(keyId, secret);

        if (request == null || !validator.validate(request).isEmpty()) {
            throw error(HttpStatus.BAD_REQUEST, "Invalid telemetry payload");
        }

        var device = devices.findByIdForUpdate(identity.deviceId())
                .orElseThrow(() -> error(
                        HttpStatus.UNAUTHORIZED, "Invalid device credential"));

        // Rotation and revocation use the same device lock. Recheck after
        // acquiring it so credentials revoked while waiting cannot write.
        var confirmed = authentication.authenticate(keyId, secret);
        if (!identity.equals(confirmed)) {
            throw error(HttpStatus.UNAUTHORIZED, "Invalid device credential");
        }

        if (!"ACTIVE".equalsIgnoreCase(device.getStatus())) {
            throw error(HttpStatus.FORBIDDEN, "Device is inactive");
        }
        if (!request.deviceCode().equals(device.getDeviceCode())) {
            throw error(HttpStatus.FORBIDDEN,
                    "Device identity does not match credential");
        }

        String hash = TelemetryPayloadHasher.hash(request);
        var existing = store.findSample(
                identity.deviceId(), request.bootId(), request.sequence());

        // Successful retries retain their original receipt and reception time.
        // They never refresh online status or change the stored reading.
        if (existing.isPresent()) {
            var sample = existing.orElseThrow();
            if (!hash.equals(sample.payloadHash())) {
                throw error(HttpStatus.CONFLICT,
                        "Sample identity already has different content");
            }
            return new Receipt(
                    sample.id(), request.bootId(), request.sequence(),
                    sample.receivedAt(), true);
        }

        Instant receivedAt = Instant.now();
        if (request.measuredAt() != null
                && (request.measuredAt().isAfter(
                        receivedAt.plus(Duration.ofMinutes(5)))
                    || request.measuredAt().isBefore(
                        receivedAt.minus(Duration.ofHours(24))))) {
            throw error(HttpStatus.BAD_REQUEST,
                    "Measurement timestamp is outside the accepted window");
        }

        var boot = store.findBoot(identity.deviceId(), request.bootId());
        if (boot.isPresent() && boot.orElseThrow().retiredAt() != null) {
            throw error(HttpStatus.CONFLICT, "Boot has been retired");
        }

        if (boot.isEmpty()) {
            // The firmware must deliver startup sample 1 before later samples.
            if (request.sequence() != 1L) {
                throw error(HttpStatus.CONFLICT,
                        "A new boot must begin with sequence 1");
            }

            var active = store.findActiveBoot(identity.deviceId());
            if (active.isPresent()) {
                var last = store.findLastSample(
                        identity.deviceId(), active.orElseThrow().bootId());
                if (last.isPresent()) {
                    rejectBackwardTime(request, last.orElseThrow());
                }
            }
        } else {
            var last = store.findLastSample(
                    identity.deviceId(), request.bootId());
            if (last.isPresent()) {
                var previous = last.orElseThrow();
                if (request.sequence() <= previous.sequence()
                        || request.uptimeMs() < previous.uptimeMs()) {
                    throw error(HttpStatus.CONFLICT,
                            "Sample sequence or uptime moved backwards");
                }
                rejectBackwardTime(request, previous);
            }
        }

        if (boot.isEmpty()) {
            store.insertBoot(
                    identity.deviceId(), request.bootId(), receivedAt);
        }

        long readingId = store.insertSample(
                identity.deviceId(), request, hash, receivedAt);

        if (boot.isEmpty()) {
            store.retireOtherBoots(
                    identity.deviceId(), request.bootId(), receivedAt);
        }

        return new Receipt(
                readingId, request.bootId(), request.sequence(),
                receivedAt, false);
    }

    private static void rejectBackwardTime(
            TelemetryRequest request, DeviceTelemetryStore.Sample previous
    ) {
        if (request.measuredAt() != null && previous.measuredAt() != null
                && request.measuredAt().isBefore(previous.measuredAt())) {
            throw error(HttpStatus.CONFLICT,
                    "Measurement timestamp moved backwards");
        }
    }

    private static ResponseStatusException error(
            HttpStatus status, String reason
    ) {
        return new ResponseStatusException(status, reason);
    }

    public record Receipt(
            long readingId, UUID bootId, long sequence,
            Instant receivedAt, boolean duplicate
    ) {}
}