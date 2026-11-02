package com.hykon.iot_backend.service;

import com.hykon.iot_backend.dto.TelemetryRequest;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class TelemetryPayloadHasherTest {

    private static final UUID BOOT =
            UUID.fromString("d40189cd-e749-433a-881d-362d78196750");

    private static BigDecimal number(String value) {
        return value == null ? null : new BigDecimal(value);
    }

    private TelemetryRequest sample(
            String batteryVoltage, String batteryCurrent, String soc,
            Boolean gridPresent, String gridVoltage, String frequency,
            String gridPower, String loadVoltage, String loadCurrent,
            String loadPower, String loadPercent
    ) {
        return new TelemetryRequest(
                1, "HALO1200-1", BOOT, 1L, null, 5000L,
                true, TelemetryRequest.OperatingMode.LINE,
                new TelemetryRequest.Battery(
                        number(batteryVoltage), number(batteryCurrent),
                        number(soc)),
                new TelemetryRequest.Grid(
                        gridPresent, number(gridVoltage), number(frequency),
                        number(gridPower)),
                new TelemetryRequest.Load(
                        number(loadVoltage), number(loadCurrent),
                        number(loadPower), number(loadPercent))
        );
    }

    private TelemetryRequest sample() {
        return sample(
                "51.2", "2", "80", true, "230", "50", "750",
                "230", "2.61", "600", "50");
    }

    @Test
    void producesStableLowercaseSha256ForIdenticalSamples() {
        String first = TelemetryPayloadHasher.hash(sample());

        assertTrue(first.matches("[0-9a-f]{64}"));
        assertEquals(first, TelemetryPayloadHasher.hash(sample()));
    }

    @Test
    void treatsEquivalentDecimalRepresentationsAsIdentical() {
        var scaled = sample(
                "51.200", "2.00", "8E+1", true, "230.0", "50.00",
                "750.000", "2.30E+2", "2.610", "600.0", "50.000");

        assertEquals(
                TelemetryPayloadHasher.hash(sample()),
                TelemetryPayloadHasher.hash(scaled)
        );
    }

    @Test
    void distinguishesUnsupportedMeasurementsFromActualZero() {
        var unsupported = sample(
                null, null, null, null, null, null, null,
                null, null, null, null);
        var zero = sample(
                "0", "0", "0", false, "0", "0", "0",
                "0", "0", "0", "0");
        var signedZero = sample(
                "0.00", "-0.0", "0", false, "0", "0", "-0.00",
                "0", "0", "0", "0");

        assertNotEquals(
                TelemetryPayloadHasher.hash(unsupported),
                TelemetryPayloadHasher.hash(zero)
        );
        assertEquals(
                TelemetryPayloadHasher.hash(zero),
                TelemetryPayloadHasher.hash(signedZero)
        );
    }

    @Test
    void detectsChangesInEveryMeasurementField() {
        String[] values = {
                "51.2", "2", "80", "230", "50", "750",
                "230", "2.61", "600", "50"
        };
        String original = TelemetryPayloadHasher.hash(sample());

        for (int index = 0; index < values.length; index++) {
            String[] changed = values.clone();
            changed[index] = new BigDecimal(changed[index])
                    .add(BigDecimal.ONE).toString();

            var altered = sample(
                    changed[0], changed[1], changed[2], true,
                    changed[3], changed[4], changed[5],
                    changed[6], changed[7], changed[8], changed[9]);

            assertNotEquals(
                    original, TelemetryPayloadHasher.hash(altered),
                    "Changed measurement index " + index);
        }

        assertNotEquals(original, TelemetryPayloadHasher.hash(sample(
                "51.2", "2", "80", false, "230", "50", "750",
                "230", "2.61", "600", "50")));
    }

    @Test
    void detectsChangesInIdentityTimingAndOperatingMetadata() {
        var base = sample();
        String original = TelemetryPayloadHasher.hash(base);

        Object[][] variants = {
                {2, base.deviceCode(), BOOT, 1L, null, 5000L, true,
                        TelemetryRequest.OperatingMode.LINE},
                {1, "OTHER-DEVICE", BOOT, 1L, null, 5000L, true,
                        TelemetryRequest.OperatingMode.LINE},
                {1, base.deviceCode(), UUID.randomUUID(), 1L, null, 5000L,
                        true, TelemetryRequest.OperatingMode.LINE},
                {1, base.deviceCode(), BOOT, 2L, null, 5000L, true,
                        TelemetryRequest.OperatingMode.LINE},
                {1, base.deviceCode(), BOOT, 1L,
                        Instant.parse("2026-11-02T04:00:00Z"), 5000L, true,
                        TelemetryRequest.OperatingMode.LINE},
                {1, base.deviceCode(), BOOT, 1L, null, 5001L, true,
                        TelemetryRequest.OperatingMode.LINE},
                {1, base.deviceCode(), BOOT, 1L, null, 5000L, false,
                        TelemetryRequest.OperatingMode.LINE},
                {1, base.deviceCode(), BOOT, 1L, null, 5000L, true,
                        TelemetryRequest.OperatingMode.BATTERY}
        };

        for (Object[] variant : variants) {
            var altered = new TelemetryRequest(
                    (Integer) variant[0], (String) variant[1],
                    (UUID) variant[2], (Long) variant[3],
                    (Instant) variant[4], (Long) variant[5],
                    (Boolean) variant[6],
                    (TelemetryRequest.OperatingMode) variant[7],
                    base.battery(), base.grid(), base.load());

            assertNotEquals(original, TelemetryPayloadHasher.hash(altered));
        }
    }
}