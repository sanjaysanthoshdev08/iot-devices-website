package com.hykon.iot_backend.repository;

import com.hykon.iot_backend.dto.TelemetryRequest;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Mutations must run in the ingestion transaction after locking the device.
 * Unique constraints remain authoritative; conflicts are never overwritten.
 */
@Repository
public class DeviceTelemetryStore {

    private final JdbcTemplate jdbc;

    public DeviceTelemetryStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record Boot(
            long id, UUID bootId, Instant firstReceivedAt, Instant retiredAt
    ) {}

    public record Sample(
            long id, String payloadHash, long sequence, long uptimeMs,
            Instant measuredAt, Instant receivedAt
    ) {}

    public Optional<Boot> findBoot(long deviceId, UUID bootId) {
        return optional("""
                SELECT id, boot_id, first_received_at, retired_at
                FROM device_telemetry_boots
                WHERE device_id = ? AND boot_id = ?
                """, (row, index) -> new Boot(
                row.getLong("id"),
                row.getObject("boot_id", UUID.class),
                instant(row, "first_received_at"),
                instant(row, "retired_at")
        ), deviceId, bootId);
    }

    public long insertBoot(long deviceId, UUID bootId, Instant receivedAt) {
        return jdbc.queryForObject("""
                INSERT INTO device_telemetry_boots
                    (device_id, boot_id, first_received_at)
                VALUES (?, ?, ?)
                RETURNING id
                """, Long.class, deviceId, bootId, timestamp(receivedAt));
    }

    public int retireOtherBoots(
            long deviceId, UUID currentBootId, Instant retiredAt
    ) {
        return jdbc.update("""
                UPDATE device_telemetry_boots
                SET retired_at = GREATEST(?, first_received_at)
                WHERE device_id = ? AND boot_id <> ? AND retired_at IS NULL
                """, timestamp(retiredAt), deviceId, currentBootId);
    }

    public Optional<Sample> findSample(
            long deviceId, UUID bootId, long sequence
    ) {
        return optional("""
                SELECT id, payload_hash, sequence, uptime_ms,
                       measured_at, received_at
                FROM device_telemetry_readings
                WHERE device_id = ? AND boot_id = ? AND sequence = ?
                """, (row, index) -> new Sample(
                row.getLong("id"),
                row.getString("payload_hash"),
                row.getLong("sequence"),
                row.getLong("uptime_ms"),
                instant(row, "measured_at"),
                instant(row, "received_at")
        ), deviceId, bootId, sequence);
    }

    public Optional<Boot> findActiveBoot(long deviceId) {
        return optional("""
                SELECT id, boot_id, first_received_at, retired_at
                FROM device_telemetry_boots
                WHERE device_id = ? AND retired_at IS NULL
                """, (row, index) -> new Boot(
                row.getLong("id"),
                row.getObject("boot_id", UUID.class),
                instant(row, "first_received_at"),
                instant(row, "retired_at")
        ), deviceId);
    }

    public Optional<Sample> findLastSample(long deviceId, UUID bootId) {
        return optional("""
                SELECT id, payload_hash, sequence, uptime_ms,
                       measured_at, received_at
                FROM device_telemetry_readings
                WHERE device_id = ? AND boot_id = ?
                  AND sequence = (
                      SELECT MAX(sequence)
                      FROM device_telemetry_readings
                      WHERE device_id = ? AND boot_id = ?
                  )
                """, (row, index) -> new Sample(
                row.getLong("id"),
                row.getString("payload_hash"),
                row.getLong("sequence"),
                row.getLong("uptime_ms"),
                instant(row, "measured_at"),
                instant(row, "received_at")
        ), deviceId, bootId, deviceId, bootId);
    }

    public long insertSample(
            long deviceId, TelemetryRequest request,
            String payloadHash, Instant receivedAt
    ) {
        var battery = request.battery();
        var grid = request.grid();
        var load = request.load();

        return jdbc.queryForObject("""
                INSERT INTO device_telemetry_readings (
                    device_id, boot_id, sequence, schema_version,
                    measured_at, received_at, uptime_ms, simulated,
                    operating_mode, payload_hash,
                    battery_voltage_v, battery_current_a, battery_soc_percent,
                    grid_present, grid_voltage_v, grid_frequency_hz,
                    grid_input_power_w,
                    load_voltage_v, load_current_a, load_output_power_w,
                    load_percent
                )
                VALUES (
                    ?, ?, ?, ?, ?, ?, ?, ?, ?, ?,
                    ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?
                )
                RETURNING id
                """, Long.class,
                deviceId, request.bootId(), request.sequence(),
                request.schemaVersion(), timestamp(request.measuredAt()),
                timestamp(receivedAt), request.uptimeMs(), request.simulated(),
                request.operatingMode().name(), payloadHash,
                battery.voltageV(), battery.currentA(), battery.socPercent(),
                grid.present(), grid.voltageV(), grid.frequencyHz(),
                grid.inputPowerW(),
                load.voltageV(), load.currentA(), load.outputPowerW(),
                load.loadPercent());
    }

    private <T> Optional<T> optional(
            String sql, RowMapper<T> mapper, Object... arguments
    ) {
        try {
            return Optional.ofNullable(
                    jdbc.queryForObject(sql, mapper, arguments));
        } catch (EmptyResultDataAccessException exception) {
            return Optional.empty();
        }
    }

    private static Timestamp timestamp(Instant value) {
        return value == null ? null : Timestamp.from(value);
    }

    private static Instant instant(ResultSet row, String column)
            throws SQLException {
        Timestamp value = row.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }
}