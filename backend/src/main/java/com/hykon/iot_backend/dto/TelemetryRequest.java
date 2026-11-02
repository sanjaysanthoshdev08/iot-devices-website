package com.hykon.iot_backend.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record TelemetryRequest(
        @NotNull @Min(1) @Max(1) Integer schemaVersion,
        @NotBlank @Size(max = 100) String deviceCode,
        @NotNull UUID bootId,
        @NotNull @Positive Long sequence,
        Instant measuredAt,
        @NotNull @PositiveOrZero Long uptimeMs,
        @NotNull Boolean simulated,
        @NotNull OperatingMode operatingMode,
        @NotNull @Valid Battery battery,
        @NotNull @Valid Grid grid,
        @NotNull @Valid Load load
) {
    public enum OperatingMode {
        LINE, BATTERY, BYPASS, STANDBY, FAULT, UNKNOWN
    }

    public record Battery(
            @DecimalMin("0") BigDecimal voltageV,
            BigDecimal currentA,
            @DecimalMin("0") @DecimalMax("100") BigDecimal socPercent
    ) {}

    public record Grid(
            Boolean present,
            @DecimalMin("0") BigDecimal voltageV,
            @DecimalMin("0") BigDecimal frequencyHz,
            BigDecimal inputPowerW
    ) {}

    public record Load(
            @DecimalMin("0") BigDecimal voltageV,
            @DecimalMin("0") BigDecimal currentA,
            @DecimalMin("0") BigDecimal outputPowerW,
            @DecimalMin("0") BigDecimal loadPercent
    ) {}
}