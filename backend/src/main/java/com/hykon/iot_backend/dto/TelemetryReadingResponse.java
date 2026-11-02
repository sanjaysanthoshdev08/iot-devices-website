package com.hykon.iot_backend.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Website telemetry response. Contains no device credential or payload hash.
 */
public record TelemetryReadingResponse(
        long readingId,
        long deviceId,
        int schemaVersion,
        UUID bootId,
        long sequence,
        Instant measuredAt,
        Instant receivedAt,
        long uptimeMs,
        boolean simulated,
        TelemetryRequest.OperatingMode operatingMode,
        TelemetryRequest.Battery battery,
        TelemetryRequest.Grid grid,
        TelemetryRequest.Load load
) {}