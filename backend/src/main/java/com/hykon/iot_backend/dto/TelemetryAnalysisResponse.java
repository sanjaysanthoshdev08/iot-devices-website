package com.hykon.iot_backend.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record TelemetryAnalysisResponse(
        long deviceId,
        Instant from,
        Instant to,
        boolean simulated,
        String timeBasis,
        long sampleCount,
        List<MetricSummary> metrics,
        TelemetryHistoryResponse history
) {
    public TelemetryAnalysisResponse {
        metrics = List.copyOf(metrics);
    }

    public record MetricSummary(
            String metric,
            String label,
            String unit,
            long validSampleCount,
            Extreme minimum,
            Extreme maximum
    ) {}

    public record Extreme(
            BigDecimal value,
            Instant receivedAt,
            Instant measuredAt,
            long readingId
    ) {}
}