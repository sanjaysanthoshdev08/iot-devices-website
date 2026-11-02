package com.hykon.iot_backend.integration;

import com.hykon.iot_backend.dto.TelemetryRequest;
import com.hykon.iot_backend.repository.DeviceTelemetryStore;
import com.hykon.iot_backend.service.TelemetryPayloadHasher;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class DeviceTelemetryStoreIntegrationTest {

    private Connection connection;
    private JdbcTemplate jdbc;
    private DeviceTelemetryStore store;
    private final UUID boot = UUID.randomUUID();
    private final Instant received =
            Instant.parse("2026-11-02T05:00:00.123456Z");

    @BeforeEach
    void prepareGuardedSchema() throws Exception {
        String password = System.getenv("HYKON_GATE_TEST_DB_PASSWORD");
        if (password == null || password.isBlank()) {
            throw new IllegalStateException(
                    "HYKON_GATE_TEST_DB_PASSWORD is required");
        }

        connection = DriverManager.getConnection(
                "jdbc:postgresql://localhost:5432/hykon_iot_gate_test",
                "postgres", password);

        try (var statement = connection.createStatement();
             var result = statement.executeQuery("SELECT current_database()")) {
            assertTrue(result.next());
            assertEquals("hykon_iot_gate_test", result.getString(1),
                    "Refusing to write outside the guarded test database");
        }

        connection.setAutoCommit(false);
        jdbc = new JdbcTemplate(
                new SingleConnectionDataSource(connection, true));

        String schema = "telemetry_store_test_" +
                UUID.randomUUID().toString().replace("-", "");
        jdbc.execute("CREATE SCHEMA " + schema);
        jdbc.execute("SET LOCAL search_path TO " + schema + ", pg_catalog");
        jdbc.execute("CREATE TABLE users (id BIGINT PRIMARY KEY)");
        jdbc.execute("CREATE TABLE devices (id BIGINT PRIMARY KEY)");
        jdbc.execute("INSERT INTO users VALUES (1)");
        jdbc.execute("INSERT INTO devices VALUES (1), (2)");
        jdbc.execute(Files.readString(
                Path.of("sql", "005_create_device_telemetry.sql")));

        store = new DeviceTelemetryStore(jdbc);
    }

    @AfterEach
    void rollbackEverything() throws Exception {
        if (connection != null) {
            try {
                if (!connection.getAutoCommit()) {
                    connection.rollback();
                }
            } finally {
                connection.close();
            }
        }
    }

    private TelemetryRequest sample(boolean unsupported) {
        return new TelemetryRequest(
                1, "HALO1200-1", boot, 1L,
                unsupported ? null : received.minusSeconds(1),
                5000L, true, TelemetryRequest.OperatingMode.LINE,
                new TelemetryRequest.Battery(
                        unsupported ? null : new BigDecimal("51.2"),
                        unsupported ? null : new BigDecimal("-2"),
                        unsupported ? null : new BigDecimal("80")),
                new TelemetryRequest.Grid(
                        unsupported ? null : true,
                        unsupported ? null : new BigDecimal("230"),
                        unsupported ? null : new BigDecimal("50"),
                        unsupported ? null : new BigDecimal("-100")),
                new TelemetryRequest.Load(
                        unsupported ? null : new BigDecimal("230"),
                        unsupported ? null : new BigDecimal("2.61"),
                        unsupported ? null : new BigDecimal("600"),
                        unsupported ? null : new BigDecimal("130")));
    }

    private long insert(long deviceId, TelemetryRequest request) {
        return store.insertSample(
                deviceId, request, TelemetryPayloadHasher.hash(request),
                received);
    }

    private long sampleCount() {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM device_telemetry_readings", Long.class);
    }

    private void assertNumber(
            String column, String expected, long readingId
    ) {
        BigDecimal actual = jdbc.queryForObject(
                "SELECT " + column +
                        " FROM device_telemetry_readings WHERE id = ?",
                BigDecimal.class, readingId);
        assertNotNull(actual);
        assertEquals(0, new BigDecimal(expected).compareTo(actual), column);
    }

    @Test
    void roundTripsAllMeasurementsMetadataAndHash() {
        long bootId = store.insertBoot(1L, boot, received);
        var storedBoot = store.findBoot(1L, boot).orElseThrow();
        assertEquals(bootId, storedBoot.id());
        assertEquals(received, storedBoot.firstReceivedAt());
        assertNull(storedBoot.retiredAt());

        var request = sample(false);
        long id = insert(1L, request);
        var stored = store.findSample(1L, boot, 1L).orElseThrow();

        assertEquals(id, stored.id());
        assertEquals(TelemetryPayloadHasher.hash(request), stored.payloadHash());
        assertEquals(1L, stored.sequence());
        assertEquals(5000L, stored.uptimeMs());
        assertEquals(request.measuredAt(), stored.measuredAt());
        assertEquals(received, stored.receivedAt());

        String[][] numbers = {
                {"battery_voltage_v", "51.2"},
                {"battery_current_a", "-2"},
                {"battery_soc_percent", "80"},
                {"grid_voltage_v", "230"},
                {"grid_frequency_hz", "50"},
                {"grid_input_power_w", "-100"},
                {"load_voltage_v", "230"},
                {"load_current_a", "2.61"},
                {"load_output_power_w", "600"},
                {"load_percent", "130"}
        };
        for (String[] number : numbers) {
            assertNumber(number[0], number[1], id);
        }

        assertEquals(Boolean.TRUE, jdbc.queryForObject(
                "SELECT grid_present FROM device_telemetry_readings WHERE id = ?",
                Boolean.class, id));
        assertEquals(Boolean.TRUE, jdbc.queryForObject(
                "SELECT simulated FROM device_telemetry_readings WHERE id = ?",
                Boolean.class, id));
        assertEquals("LINE", jdbc.queryForObject(
                "SELECT operating_mode FROM device_telemetry_readings WHERE id = ?",
                String.class, id));
        assertEquals(1, jdbc.queryForObject(
                "SELECT schema_version FROM device_telemetry_readings WHERE id = ?",
                Integer.class, id));
    }

    @Test
    void preservesUnsupportedMeasurementsAsNull() {
        store.insertBoot(1L, boot, received);
        long id = insert(1L, sample(true));

        var row = jdbc.queryForMap("""
                SELECT measured_at, battery_voltage_v, battery_current_a,
                       battery_soc_percent, grid_present, grid_voltage_v,
                       grid_frequency_hz, grid_input_power_w,
                       load_voltage_v, load_current_a, load_output_power_w,
                       load_percent
                FROM device_telemetry_readings WHERE id = ?
                """, id);

        assertEquals(12, row.size());
        row.forEach((column, value) -> assertNull(value, column));
    }

    @Test
    void scopesBootsAndSampleLookupsToTheirDevice() {
        assertTrue(store.findBoot(1L, boot).isEmpty());
        assertTrue(store.findSample(1L, boot, 1L).isEmpty());

        store.insertBoot(1L, boot, received);
        long firstId = insert(1L, sample(false));

        assertTrue(store.findBoot(2L, boot).isEmpty());
        assertTrue(store.findSample(2L, boot, 1L).isEmpty());
        assertTrue(store.findSample(1L, boot, 2L).isEmpty());

        store.insertBoot(2L, boot, received);
        long secondId = insert(2L, sample(false));

        assertNotEquals(firstId, secondId);
        assertEquals(firstId,
                store.findSample(1L, boot, 1L).orElseThrow().id());
        assertEquals(secondId,
                store.findSample(2L, boot, 1L).orElseThrow().id());
        assertEquals(2L, sampleCount());
    }

    @Test
    void retiresOnlyOtherBootsOfTheSameDeviceWithoutDeletingReadings() {
        UUID replacement = UUID.randomUUID();
        store.insertBoot(1L, boot, received);
        insert(1L, sample(false));
        store.insertBoot(1L, replacement, received.plusSeconds(5));
        store.insertBoot(2L, boot, received);

        assertEquals(1, store.retireOtherBoots(
                1L, replacement, received.plusSeconds(5)));
        assertEquals(0, store.retireOtherBoots(
                1L, replacement, received.plusSeconds(6)));

        assertEquals(received.plusSeconds(5),
                store.findBoot(1L, boot).orElseThrow().retiredAt());
        assertNull(store.findBoot(1L, replacement).orElseThrow().retiredAt());
        assertNull(store.findBoot(2L, boot).orElseThrow().retiredAt());
        assertEquals(1L, sampleCount());
    }

    @Test
    void rejectsDuplicateSamplesAndSupportsTransactionRollback()
            throws Exception {
        store.insertBoot(1L, boot, received);
        long originalId = insert(1L, sample(false));

        var beforeDuplicate = connection.setSavepoint();
        try {
            assertThrows(DuplicateKeyException.class,
                    () -> insert(1L, sample(false)));
        } finally {
            connection.rollback(beforeDuplicate);
            connection.releaseSavepoint(beforeDuplicate);
        }

        assertEquals(1L, sampleCount());
        assertEquals(originalId,
                store.findSample(1L, boot, 1L).orElseThrow().id());

        var beforeChanges = connection.setSavepoint();
        UUID replacement = UUID.randomUUID();
        store.insertBoot(1L, replacement, received.plusSeconds(5));
        store.retireOtherBoots(1L, replacement, received.plusSeconds(5));
        connection.rollback(beforeChanges);
        connection.releaseSavepoint(beforeChanges);

        assertTrue(store.findBoot(1L, replacement).isEmpty());
        assertNull(store.findBoot(1L, boot).orElseThrow().retiredAt());
        assertEquals(1L, sampleCount());
    }
}