package com.hykon.iot_backend.service;

import com.hykon.iot_backend.dto.TelemetryRequest;
import com.hykon.iot_backend.dto.TelemetryV2Request;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Turns a universal (v2) payload into the existing v1 reading plus "extras".
 * Known measurement names fill the dashboard columns; unknown names go to the extras JSON.
 */
public final class TelemetryV2Translator {

    private TelemetryV2Translator() {}

    public record Translation(TelemetryRequest reading, String extrasJson) {}

    public static Translation translate(TelemetryV2Request request) {
        Map<String, BigDecimal> known = new HashMap<>();
        Map<String, TelemetryV2Request.Measurement> extras = new LinkedHashMap<>();
        Map<String, Boolean> seen = new HashMap<>();

        for (var m : request.measurements()) {
            if (seen.put(m.key(), true) != null) {
                throw new IllegalArgumentException("Duplicate measurement: " + m.key());
            }
            if (isKnown(m.key())) {
                known.put(m.key(), m.value());
            } else {
                extras.put(m.key(), m);
            }
        }

        var battery = new TelemetryRequest.Battery(
                known.get("battery_voltage_v"),
                known.get("battery_current_a"),
                known.get("battery_soc_percent"));
        BigDecimal gridPresent = known.get("grid_present");
        var grid = new TelemetryRequest.Grid(
                gridPresent == null ? null : gridPresent.signum() != 0,
                known.get("grid_voltage_v"),
                known.get("grid_frequency_hz"),
                known.get("grid_input_power_w"));
        var load = new TelemetryRequest.Load(
                known.get("load_voltage_v"),
                known.get("load_current_a"),
                known.get("load_output_power_w"),
                known.get("load_percent"));

        var reading = new TelemetryRequest(
                1,
                request.deviceCode(),
                request.bootId(),
                request.sequence(),
                request.measuredAt(),
                request.uptimeMs(),
                request.simulated(),
                request.operatingMode() == null
                        ? TelemetryRequest.OperatingMode.UNKNOWN
                        : request.operatingMode(),
                battery, grid, load);

        return new Translation(reading, extras.isEmpty() ? null : toJson(extras));
    }

    private static boolean isKnown(String key) {
        return switch (key) {
            case "battery_voltage_v", "battery_current_a", "battery_soc_percent",
                 "grid_present", "grid_voltage_v", "grid_frequency_hz", "grid_input_power_w",
                 "load_voltage_v", "load_current_a", "load_output_power_w", "load_percent" -> true;
            default -> false;
        };
    }

    /** Keys and units are validated by pattern (no quotes or backslashes), so no escaping is needed. */
    private static String toJson(Map<String, TelemetryV2Request.Measurement> extras) {
        var sb = new StringBuilder("{");
        boolean first = true;
        for (var e : extras.entrySet()) {
            if (!first) sb.append(',');
            first = false;
            var m = e.getValue();
            sb.append('"').append(e.getKey()).append("\":{\"value\":")
              .append(m.value() == null ? "null" : m.value().toPlainString())
              .append(",\"unit\":")
              .append(m.unit() == null || m.unit().isEmpty() ? "null" : "\"" + m.unit() + "\"")
              .append('}');
        }
        return sb.append('}').toString();
    }
}
