package com.hykon.iot_backend.service;

import com.hykon.iot_backend.dto.TelemetryRequest;
import com.hykon.iot_backend.dto.TelemetryV2Request;
import com.hykon.iot_backend.dto.TelemetryV2Request.Measurement;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TelemetryV2TranslatorTest {

    private static Measurement m(String key, String value, String unit) {
        return new Measurement(key, value == null ? null : new BigDecimal(value), unit);
    }

    private static TelemetryV2Request request(Measurement... measurements) {
        return new TelemetryV2Request(
                2, "halo1200-100", UUID.randomUUID(), 1L, null, 1000L, false,
                null, "1.2.0", "Halo 1200", List.of(measurements));
    }

    @Test
    void knownNamesFillTheDashboardColumnsAndUnknownNamesBecomeExtras() {
        var result = TelemetryV2Translator.translate(request(
                m("battery_voltage_v", "52.4", "V"),
                m("battery_soc_percent", "87", "%"),
                m("grid_present", "1", null),
                m("grid_voltage_v", "230.1", "V"),
                m("load_output_power_w", "640", "W"),
                m("pv_power_w", "1200", "W"),
                m("heatsink_temp_c", "41.5", "C")));

        var reading = result.reading();
        assertEquals(1, reading.schemaVersion());
        assertEquals(new BigDecimal("52.4"), reading.battery().voltageV());
        assertEquals(new BigDecimal("87"), reading.battery().socPercent());
        assertNull(reading.battery().currentA());
        assertTrue(reading.grid().present());
        assertEquals(new BigDecimal("640"), reading.load().outputPowerW());
        assertEquals(TelemetryRequest.OperatingMode.UNKNOWN, reading.operatingMode());
        assertEquals("{\"pv_power_w\":{\"value\":1200,\"unit\":\"W\"},"
                + "\"heatsink_temp_c\":{\"value\":41.5,\"unit\":\"C\"}}", result.extrasJson());
    }

    @Test
    void noExtrasMeansNoExtrasJsonAndGridAbsentIsFalse() {
        var result = TelemetryV2Translator.translate(request(m("grid_present", "0", null)));
        assertNull(result.extrasJson());
        assertFalse(result.reading().grid().present());
    }

    @Test
    void unavailableValuesStayNull() {
        var result = TelemetryV2Translator.translate(request(
                m("battery_voltage_v", null, "V"), m("fan_rpm", null, "rpm")));
        assertNull(result.reading().battery().voltageV());
        assertEquals("{\"fan_rpm\":{\"value\":null,\"unit\":\"rpm\"}}", result.extrasJson());
    }

    @Test
    void duplicateNamesAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> TelemetryV2Translator.translate(request(
                m("battery_voltage_v", "52", "V"), m("battery_voltage_v", "53", "V"))));
    }

    @Test
    void formatRulesRejectBadNamesAndUnits() {
        var validator = Validation.buildDefaultValidatorFactory().getValidator();
        assertTrue(validator.validate(request(m("battery_voltage_v", "52", "V"))).isEmpty());
        assertFalse(validator.validate(request(m("Bad Name", "1", null))).isEmpty());
        assertFalse(validator.validate(request(m("ok_name", "1", "a\"b"))).isEmpty());
        assertFalse(validator.validate(request()).isEmpty());
    }
}
