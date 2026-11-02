package com.hykon.iot_backend.repository;

import com.hykon.iot_backend.dto.TelemetryReadingResponse;
import com.hykon.iot_backend.dto.TelemetryRequest;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Caller must verify website session and device access before querying.
 */
@Repository
public class DeviceTelemetryReadStore {

    private final JdbcTemplate jdbc;

    private static final String COLUMNS = """
            r.id, r.device_id, r.schema_version, r.boot_id, r.sequence,
            r.measured_at, r.received_at, r.uptime_ms, r.simulated,
            r.operating_mode,
            r.battery_voltage_v, r.battery_current_a, r.battery_soc_percent,
            r.grid_present, r.grid_voltage_v, r.grid_frequency_hz,
            r.grid_input_power_w,
            r.load_voltage_v, r.load_current_a, r.load_output_power_w,
            r.load_percent
            """;

    public DeviceTelemetryReadStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<TelemetryReadingResponse> findLatest(long deviceId) {
        requireDeviceId(deviceId);
        try {
            return Optional.ofNullable(jdbc.queryForObject(
                    "SELECT " + COLUMNS + """
                    FROM device_telemetry_readings r
                    JOIN device_telemetry_boots b
                      ON b.device_id = r.device_id AND b.boot_id = r.boot_id
                    WHERE r.device_id = ? AND b.retired_at IS NULL
                      AND r.sequence = (
                          SELECT MAX(s.sequence)
                          FROM device_telemetry_readings s
                          WHERE s.device_id = r.device_id
                            AND s.boot_id = r.boot_id
                      )
                    """, this::map, deviceId));
        } catch (EmptyResultDataAccessException exception) {
            return Optional.empty();
        }
    }

    public Page<TelemetryReadingResponse> history(
            long deviceId, int page, int size
    ) {
        requireDeviceId(deviceId);
        if (page < 0 || size < 1 || size > 100) {
            throw new IllegalArgumentException(
                    "Page must be nonnegative and size must be between 1 and 100");
        }

        Long total = jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM device_telemetry_readings
                WHERE device_id = ?
                """, Long.class, deviceId);

        var content = jdbc.query(
                "SELECT " + COLUMNS + """
                FROM device_telemetry_readings r
                JOIN device_telemetry_boots b
                  ON b.device_id = r.device_id AND b.boot_id = r.boot_id
                WHERE r.device_id = ?
                ORDER BY b.id DESC, r.sequence DESC
                LIMIT ? OFFSET ?
                """, this::map, deviceId, size, (long) page * size);

        return new PageImpl<>(
                content, PageRequest.of(page, size),
                java.util.Objects.requireNonNull(total));
    }

    private TelemetryReadingResponse map(ResultSet row, int index)
            throws SQLException {
        return new TelemetryReadingResponse(
                row.getLong("id"),
                row.getLong("device_id"),
                row.getInt("schema_version"),
                row.getObject("boot_id", UUID.class),
                row.getLong("sequence"),
                instant(row, "measured_at"),
                instant(row, "received_at"),
                row.getLong("uptime_ms"),
                row.getBoolean("simulated"),
                TelemetryRequest.OperatingMode.valueOf(
                        row.getString("operating_mode")),
                new TelemetryRequest.Battery(
                        row.getBigDecimal("battery_voltage_v"),
                        row.getBigDecimal("battery_current_a"),
                        row.getBigDecimal("battery_soc_percent")),
                new TelemetryRequest.Grid(
                        row.getObject("grid_present", Boolean.class),
                        row.getBigDecimal("grid_voltage_v"),
                        row.getBigDecimal("grid_frequency_hz"),
                        row.getBigDecimal("grid_input_power_w")),
                new TelemetryRequest.Load(
                        row.getBigDecimal("load_voltage_v"),
                        row.getBigDecimal("load_current_a"),
                        row.getBigDecimal("load_output_power_w"),
                        row.getBigDecimal("load_percent"))
        );
    }

    private static Instant instant(ResultSet row, String column)
            throws SQLException {
        var value = row.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }

    private static void requireDeviceId(long deviceId) {
        if (deviceId <= 0) {
            throw new IllegalArgumentException("Device ID must be positive");
        }
    }
}