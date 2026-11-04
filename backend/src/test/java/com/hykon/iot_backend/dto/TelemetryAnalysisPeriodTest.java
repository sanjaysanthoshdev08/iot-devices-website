package com.hykon.iot_backend.dto;

import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import static org.junit.jupiter.api.Assertions.*;

class TelemetryAnalysisPeriodTest {
    private final Instant start = Instant.parse("2026-11-01T00:00:00Z");

    @Test
    void acceptsACompleteMonthAndExplicitSimulationSelection() {
        var period = new TelemetryAnalysisPeriod(
                start, Instant.parse("2026-12-01T00:00:00Z"), true);
        assertEquals(start, period.from());
        assertTrue(period.simulated());
    }

    @Test
    void requiresBothBoundsAndSimulationSelection() {
        assertThrows(IllegalArgumentException.class,
                () -> new TelemetryAnalysisPeriod(null, start, true));
        assertThrows(IllegalArgumentException.class,
                () -> new TelemetryAnalysisPeriod(start, null, true));
        assertThrows(IllegalArgumentException.class,
                () -> new TelemetryAnalysisPeriod(
                        start, start.plusSeconds(1), null));
    }

    @Test
    void rejectsEmptyAndReversedPeriods() {
        assertThrows(IllegalArgumentException.class,
                () -> new TelemetryAnalysisPeriod(start, start, true));
        assertThrows(IllegalArgumentException.class,
                () -> new TelemetryAnalysisPeriod(
                        start, start.minusSeconds(1), true));
    }

    @Test
    void accepts366DaysButRejectsLongerRequests() {
        assertDoesNotThrow(() -> new TelemetryAnalysisPeriod(
                start, start.plus(366, ChronoUnit.DAYS), false));
        assertThrows(IllegalArgumentException.class,
                () -> new TelemetryAnalysisPeriod(
                        start, start.plus(366, ChronoUnit.DAYS).plusSeconds(1), false));
    }

    @Test
    void keepsActualAndSimulatedSelectionsDistinct() {
        var end = start.plusSeconds(1);
        assertNotEquals(
                new TelemetryAnalysisPeriod(start, end, true),
                new TelemetryAnalysisPeriod(start, end, false));
    }
}