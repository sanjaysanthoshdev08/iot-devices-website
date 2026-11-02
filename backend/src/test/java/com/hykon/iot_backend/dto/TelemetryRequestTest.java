package com.hykon.iot_backend.dto;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class TelemetryRequestTest {

    private static final ValidatorFactory FACTORY =
            Validation.buildDefaultValidatorFactory();
    private static final Validator VALIDATOR = FACTORY.getValidator();

    @AfterAll
    static void closeValidator() {
        FACTORY.close();
    }

    private TelemetryRequest request(
            Integer version, String code, UUID bootId, Long sequence,
            Instant measuredAt, Long uptimeMs, Boolean simulated,
            TelemetryRequest.OperatingMode mode,
            TelemetryRequest.Battery battery,
            TelemetryRequest.Grid grid,
            TelemetryRequest.Load load
    ) {
        return new TelemetryRequest(
                version, code, bootId, sequence, measuredAt, uptimeMs,
                simulated, mode, battery, grid, load
        );
    }

    private TelemetryRequest.Battery battery() {
        return new TelemetryRequest.Battery(
                new BigDecimal("51.2"), new BigDecimal("2"),
                new BigDecimal("80")
        );
    }

    private TelemetryRequest.Grid grid() {
        return new TelemetryRequest.Grid(
                true, new BigDecimal("230"), new BigDecimal("50"),
                new BigDecimal("750")
        );
    }

    private TelemetryRequest.Load load() {
        return new TelemetryRequest.Load(
                new BigDecimal("230"), new BigDecimal("2.61"),
                new BigDecimal("600"), new BigDecimal("50")
        );
    }

    private Set<String> invalidPaths(TelemetryRequest value) {
        return VALIDATOR.validate(value).stream()
                .map(error -> error.getPropertyPath().toString())
                .collect(Collectors.toSet());
    }

    @Test
    void acceptsDashboardSampleBeforeClockSynchronization() {
        var value = request(
                1, "HALO1200-1", UUID.randomUUID(), 1L, null, 5000L,
                true, TelemetryRequest.OperatingMode.LINE,
                battery(), grid(), load()
        );
        assertTrue(invalidPaths(value).isEmpty());
        assertNull(value.measuredAt());
    }

    @Test
    void acceptsNullUnsupportedMeasurements() {
        var value = request(
                1, "HALO1200-1", UUID.randomUUID(), 1L,
                Instant.parse("2026-11-02T04:00:00Z"), 5000L,
                false, TelemetryRequest.OperatingMode.UNKNOWN,
                new TelemetryRequest.Battery(null, null, null),
                new TelemetryRequest.Grid(null, null, null, null),
                new TelemetryRequest.Load(null, null, null, null)
        );
        assertTrue(invalidPaths(value).isEmpty());
    }

    @Test
    void requiresIdentityMetadataAndMeasurementGroups() {
        var value = request(
                null, "", null, null, null, null,
                null, null, null, null, null
        );
        assertEquals(Set.of(
                "schemaVersion", "deviceCode", "bootId", "sequence",
                "uptimeMs", "simulated", "operatingMode",
                "battery", "grid", "load"
        ), invalidPaths(value));
    }

    @Test
    void rejectsUnsupportedVersionInvalidCountersAndLongCode() {
        var value = request(
                2, "x".repeat(101), UUID.randomUUID(), 0L, null, -1L,
                true, TelemetryRequest.OperatingMode.LINE,
                battery(), grid(), load()
        );
        assertEquals(Set.of(
                "schemaVersion", "deviceCode", "sequence", "uptimeMs"
        ), invalidPaths(value));
    }

    @Test
    void rejectsNegativeUnsignedMeasurementsAndExcessiveSoc() {
        var negative = new BigDecimal("-1");
        var value = request(
                1, "HALO1200-1", UUID.randomUUID(), 1L, null, 0L,
                true, TelemetryRequest.OperatingMode.LINE,
                new TelemetryRequest.Battery(
                        negative, null, new BigDecimal("101")),
                new TelemetryRequest.Grid(
                        true, negative, negative, null),
                new TelemetryRequest.Load(
                        negative, negative, negative, negative)
        );
        assertEquals(Set.of(
                "battery.voltageV", "battery.socPercent",
                "grid.voltageV", "grid.frequencyHz",
                "load.voltageV", "load.currentA",
                "load.outputPowerW", "load.loadPercent"
        ), invalidPaths(value));
    }

    @Test
    void acceptsDischargeExportAndOverloadWithoutClamping() {
        var value = request(
                1, "HALO1200-1", UUID.randomUUID(), 1L, null, 5000L,
                true, TelemetryRequest.OperatingMode.BATTERY,
                new TelemetryRequest.Battery(
                        new BigDecimal("48"), new BigDecimal("-10"),
                        new BigDecimal("60")),
                new TelemetryRequest.Grid(
                        true, new BigDecimal("230"),
                        new BigDecimal("50"), new BigDecimal("-100")),
                new TelemetryRequest.Load(
                        new BigDecimal("230"), new BigDecimal("6"),
                        new BigDecimal("1300"), new BigDecimal("130"))
        );
        assertTrue(invalidPaths(value).isEmpty());
        assertEquals(new BigDecimal("-10"), value.battery().currentA());
        assertEquals(new BigDecimal("130"), value.load().loadPercent());
    }

    @Test
    void rejectsNegativeSoc() {
        var value = request(
                1, "HALO1200-1", UUID.randomUUID(), 1L, null, 0L,
                true, TelemetryRequest.OperatingMode.LINE,
                new TelemetryRequest.Battery(
                        null, null, new BigDecimal("-1")),
                grid(), load()
        );
        assertEquals(Set.of("battery.socPercent"), invalidPaths(value));
    }
}