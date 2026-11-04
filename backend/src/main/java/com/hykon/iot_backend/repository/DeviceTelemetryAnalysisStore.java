package com.hykon.iot_backend.repository;

import com.hykon.iot_backend.dto.TelemetryAnalysisPeriod;
import com.hykon.iot_backend.dto.TelemetryAnalysisResponse.Extreme;
import com.hykon.iot_backend.dto.TelemetryAnalysisResponse.MetricSummary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

/** Caller must authorize access and use a repeatable-read transaction. */
@Repository
public class DeviceTelemetryAnalysisStore {
    private final JdbcTemplate jdbc;

    // SQL column names come only from this fixed internal list.
    private static final List<Metric> METRICS = List.of(
            new Metric("batteryVoltageV", "Battery voltage", "V", "battery_voltage_v"),
            new Metric("batteryCurrentA", "Battery current", "A", "battery_current_a"),
            new Metric("batterySocPercent", "Battery charge", "%", "battery_soc_percent"),
            new Metric("gridVoltageV", "Grid voltage", "V", "grid_voltage_v"),
            new Metric("gridFrequencyHz", "Grid frequency", "Hz", "grid_frequency_hz"),
            new Metric("gridInputPowerW", "Grid input power", "W", "grid_input_power_w"),
            new Metric("loadVoltageV", "Load voltage", "V", "load_voltage_v"),
            new Metric("loadCurrentA", "Load current", "A", "load_current_a"),
            new Metric("loadOutputPowerW", "Load output power", "W", "load_output_power_w"),
            new Metric("loadPercent", "Load percentage", "%", "load_percent")
    );

    private static final String FILTER = """
            WHERE r.device_id = ?
              AND r.received_at >= ? AND r.received_at < ?
              AND r.simulated = ?
            """;

    public DeviceTelemetryAnalysisStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record Statistics(long sampleCount, List<MetricSummary> metrics) {
        public Statistics {
            metrics = List.copyOf(metrics);
        }
    }

    private record Metric(String key, String label, String unit, String column) {}

    public Statistics summarize(long deviceId, TelemetryAnalysisPeriod period) {
        if (deviceId <= 0 || period == null) {
            throw new IllegalArgumentException("Invalid analysis request");
        }

        StringBuilder sql = new StringBuilder("SELECT COUNT(*) AS sample_count");
        for (int index = 0; index < METRICS.size(); index++) {
            String column = METRICS.get(index).column();
            String alias = "m" + index;
            sql.append(", COUNT(r.").append(column).append(") AS ")
                    .append(alias).append("_count")
                    .append(", MIN(r.").append(column).append(") AS ")
                    .append(alias).append("_min")
                    .append(", MAX(r.").append(column).append(") AS ")
                    .append(alias).append("_max");
        }
        sql.append(" FROM device_telemetry_readings r ").append(FILTER);

        var stats = jdbc.queryForMap(sql.toString(), parameters(deviceId, period));
        List<MetricSummary> summaries = new ArrayList<>();

        for (int index = 0; index < METRICS.size(); index++) {
            Metric metric = METRICS.get(index);
            String alias = "m" + index;
            long count = ((Number) stats.get(alias + "_count")).longValue();

            Extreme minimum = null;
            Extreme maximum = null;
            if (count > 0) {
                BigDecimal min = (BigDecimal) stats.get(alias + "_min");
                BigDecimal max = (BigDecimal) stats.get(alias + "_max");
                minimum = occurrence(deviceId, period, metric.column(), min);
                maximum = min.compareTo(max) == 0
                        ? minimum
                        : occurrence(deviceId, period, metric.column(), max);
            }

            summaries.add(new MetricSummary(
                    metric.key(), metric.label(), metric.unit(),
                    count, minimum, maximum));
        }

        return new Statistics(
                ((Number) stats.get("sample_count")).longValue(), summaries);
    }

    private Extreme occurrence(
            long deviceId, TelemetryAnalysisPeriod period,
            String column, BigDecimal value
    ) {
        // Equal extremes choose the earliest reception, then the lowest row ID.
        String sql = """
                SELECT r.id, r.received_at, r.measured_at,
                """ + " r." + column + " AS metric_value "
                + "FROM device_telemetry_readings r " + FILTER
                + " AND r." + column + " = ? "
                + "ORDER BY r.received_at ASC, r.id ASC LIMIT 1";

        var rows = jdbc.query(sql, (row, index) -> {
            var measured = row.getTimestamp("measured_at");
            return new Extreme(
                    row.getBigDecimal("metric_value"),
                    row.getTimestamp("received_at").toInstant(),
                    measured == null ? null : measured.toInstant(),
                    row.getLong("id"));
        }, deviceId, Timestamp.from(period.from()),
                Timestamp.from(period.to()), period.simulated(), value);

        if (rows.size() != 1) {
            throw new IllegalStateException(
                    "Analysis extreme could not be located in the same snapshot");
        }
        return rows.get(0);
    }

    private static Object[] parameters(
            long deviceId, TelemetryAnalysisPeriod period
    ) {
        return new Object[] {
                deviceId, Timestamp.from(period.from()),
                Timestamp.from(period.to()), period.simulated()
        };
    }
}