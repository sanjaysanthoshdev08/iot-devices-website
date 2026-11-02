package com.hykon.iot_backend.service;

import com.hykon.iot_backend.dto.TelemetryHistoryResponse;
import com.hykon.iot_backend.dto.TelemetryLatestResponse;
import com.hykon.iot_backend.repository.DeviceTelemetryReadStore;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
public class DeviceTelemetryQueryService {

    private final SessionAccessPolicyService accessPolicy;
    private final DeviceQueryService deviceQueries;
    private final DeviceTelemetryReadStore readings;

    public DeviceTelemetryQueryService(
            SessionAccessPolicyService accessPolicy,
            DeviceQueryService deviceQueries,
            DeviceTelemetryReadStore readings
    ) {
        this.accessPolicy = accessPolicy;
        this.deviceQueries = deviceQueries;
        this.readings = readings;
    }

    public TelemetryLatestResponse latest(
            Long deviceId, Authentication authentication
    ) {
        requireDeviceAccess(deviceId, authentication);
        return TelemetryLatestResponse.from(
                readings.findLatest(deviceId), Instant.now());
    }

    public TelemetryHistoryResponse history(
            Long deviceId, int page, int size, Authentication authentication
    ) {
        requireDeviceAccess(deviceId, authentication);

        if (page < 0 || size < 1 || size > 100) {
            throw new IllegalArgumentException(
                    "Page must be nonnegative and size must be between 1 and 100");
        }

        return TelemetryHistoryResponse.from(
                readings.history(deviceId, page, size));
    }

    private void requireDeviceAccess(
            Long deviceId, Authentication authentication
    ) {
        accessPolicy.requireVerified(authentication);
        deviceQueries.get(deviceId, authentication.getName());
    }
}