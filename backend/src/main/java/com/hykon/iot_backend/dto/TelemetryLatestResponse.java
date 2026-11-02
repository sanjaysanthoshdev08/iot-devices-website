package com.hykon.iot_backend.dto;

import java.time.Instant;
import java.util.Optional;

public record TelemetryLatestResponse(
        ConnectionStatus status,
        Instant serverTime,
        long staleAfterSeconds,
        TelemetryReadingResponse reading
) {
    public enum ConnectionStatus {
        NO_DATA, ONLINE, OFFLINE
    }

    public static TelemetryLatestResponse from(
            Optional<TelemetryReadingResponse> latest, Instant now
    ) {
        final long threshold = 30;

        if (latest.isEmpty()) {
            return new TelemetryLatestResponse(
                    ConnectionStatus.NO_DATA, now, threshold, null);
        }

        var reading = latest.orElseThrow();
        boolean fresh = !reading.receivedAt().isAfter(now)
                && !reading.receivedAt().isBefore(now.minusSeconds(threshold));

        return new TelemetryLatestResponse(
                fresh ? ConnectionStatus.ONLINE : ConnectionStatus.OFFLINE,
                now, threshold, reading);
    }
}