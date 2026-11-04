package com.hykon.iot_backend.service;

import com.hykon.iot_backend.dto.TelemetryAnalysisPeriod;
import com.hykon.iot_backend.dto.TelemetryAnalysisResponse;
import com.hykon.iot_backend.dto.TelemetryHistoryResponse;
import com.hykon.iot_backend.repository.DeviceTelemetryAnalysisStore;
import com.hykon.iot_backend.repository.DeviceTelemetryReadStore;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
public class DeviceTelemetryAnalysisService {
    private final SessionAccessPolicyService accessPolicy;
    private final DeviceQueryService devices;
    private final DeviceTelemetryReadStore readings;
    private final DeviceTelemetryAnalysisStore analysis;

    public DeviceTelemetryAnalysisService(
            SessionAccessPolicyService accessPolicy,
            DeviceQueryService devices,
            DeviceTelemetryReadStore readings,
            DeviceTelemetryAnalysisStore analysis
    ) {
        this.accessPolicy = accessPolicy;
        this.devices = devices;
        this.readings = readings;
        this.analysis = analysis;
    }

    public TelemetryAnalysisResponse analyze(
            Long deviceId, TelemetryAnalysisPeriod period,
            int page, int size, Authentication authentication
    ) {
        accessPolicy.requireVerified(authentication);
        devices.get(deviceId, authentication.getName());

        if (period == null || page < 0 || size < 1 || size > 100) {
            throw new IllegalArgumentException("Invalid analysis period or page");
        }

        var statistics = analysis.summarize(deviceId, period);
        var history = TelemetryHistoryResponse.from(
                readings.history(deviceId, page, size, period));

        return new TelemetryAnalysisResponse(
                deviceId, period.from(), period.to(), period.simulated(),
                "RECEIVED_AT", statistics.sampleCount(),
                statistics.metrics(), history);
    }
}