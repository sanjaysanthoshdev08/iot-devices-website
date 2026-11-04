package com.hykon.iot_backend.dto;

import java.time.Duration;
import java.time.Instant;

/** Reception-time range: inclusive start, exclusive end. */
public record TelemetryAnalysisPeriod(
        Instant from,
        Instant to,
        Boolean simulated
) {
    public TelemetryAnalysisPeriod {
        if (from == null || to == null || simulated == null) {
            throw new IllegalArgumentException(
                    "Start, end and simulation selection are required");
        }
        if (!from.isBefore(to)) {
            throw new IllegalArgumentException(
                    "Start must be earlier than end");
        }
        if (Duration.between(from, to).compareTo(Duration.ofDays(366)) > 0) {
            throw new IllegalArgumentException(
                    "Select a period of no more than 366 days");
        }
    }
}