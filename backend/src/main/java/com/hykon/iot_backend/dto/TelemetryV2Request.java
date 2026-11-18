package com.hykon.iot_backend.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Universal telemetry format (schema version 2). Any ESP32 inverter firmware can send a list of
 * named measurements. Well-known names fill the existing dashboard columns; other names are kept
 * as extras, so a new inverter model needs no backend change.
 */
public record TelemetryV2Request(
        @NotNull @Min(2) @Max(2) Integer schemaVersion,
        @NotBlank @Size(max = 100) String deviceCode,
        @NotNull UUID bootId,
        @NotNull @Positive Long sequence,
        Instant measuredAt,
        @NotNull @PositiveOrZero Long uptimeMs,
        @NotNull Boolean simulated,
        TelemetryRequest.OperatingMode operatingMode,
        @Size(max = 40) String firmwareVersion,
        @Size(max = 60) String model,
        @NotNull @Size(min = 1, max = 100) List<@Valid @NotNull Measurement> measurements
) {
    /** value = null means "unavailable". Booleans are sent as 1 or 0. */
    public record Measurement(
            @NotBlank @Pattern(regexp = "^[a-z][a-z0-9_]{1,47}$") String key,
            BigDecimal value,
            @Pattern(regexp = "^[A-Za-z0-9%/._-]{0,16}$") String unit
    ) {}
}
