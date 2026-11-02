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

    private com.hykon.iot_backend.repository.DeviceTelemetryReadStore reads() {
        return new com.hykon.iot_backend.repository.DeviceTelemetryReadStore(jdbc);
    }

    private TelemetryRequest sampleFor(
            UUID sampleBoot, long sequence, long uptime
    ) {
        var base = sample(false);
        return new TelemetryRequest(
                base.schemaVersion(), base.deviceCode(), sampleBoot,
                sequence, base.measuredAt(), uptime,
                base.simulated(), base.operatingMode(),
                base.battery(), base.grid(), base.load());
    }

    @Test
    void latestReadUsesActiveBootAndHighestSequence() {
        store.insertBoot(1L, boot, received);
        insert(1L, sampleFor(boot, 1L, 5000L));

        var second = sampleFor(boot, 2L, 10000L);
        long secondId = store.insertSample(
                1L, second, TelemetryPayloadHasher.hash(second),
                received.minusSeconds(1));

        assertEquals(secondId, reads().findLatest(1L).orElseThrow().readingId());

        UUID replacement = UUID.randomUUID();
        store.insertBoot(1L, replacement, received.plusSeconds(5));
        store.retireOtherBoots(1L, replacement, received.plusSeconds(5));
        long replacementId = insert(1L, sampleFor(replacement, 1L, 5000L));

        var latest = reads().findLatest(1L).orElseThrow();
        assertEquals(replacementId, latest.readingId());
        assertEquals(replacement, latest.bootId());
        assertEquals(1L, latest.sequence());
        assertEquals(1L, latest.deviceId());
        assertEquals(1, latest.schemaVersion());
        assertTrue(latest.simulated());
        assertEquals(TelemetryRequest.OperatingMode.LINE, latest.operatingMode());
    }

    @Test
    void historyPagesCountOnlyRequestedDeviceAndIncludeRetiredBoots() {
        store.insertBoot(1L, boot, received);
        long oldFirst = insert(1L, sampleFor(boot, 1L, 5000L));
        long oldSecond = insert(1L, sampleFor(boot, 2L, 10000L));

        UUID replacement = UUID.randomUUID();
        store.insertBoot(1L, replacement, received.plusSeconds(5));
        store.retireOtherBoots(1L, replacement, received.plusSeconds(5));
        long newFirst = insert(1L, sampleFor(replacement, 1L, 5000L));
        long newSecond = insert(1L, sampleFor(replacement, 2L, 10000L));

        store.insertBoot(2L, boot, received);
        insert(2L, sampleFor(boot, 1L, 5000L));

        var firstPage = reads().history(1L, 0, 2);
        var secondPage = reads().history(1L, 1, 2);
        var beyondEnd = reads().history(1L, 2, 2);

        assertEquals(4L, firstPage.getTotalElements());
        assertEquals(2, firstPage.getTotalPages());
        assertEquals(java.util.List.of(newSecond, newFirst),
                firstPage.getContent().stream()
                        .map(value -> value.readingId()).toList());
        assertEquals(4L, secondPage.getTotalElements());
        assertEquals(java.util.List.of(oldSecond, oldFirst),
                secondPage.getContent().stream()
                        .map(value -> value.readingId()).toList());
        assertTrue(beyondEnd.isEmpty());
        assertEquals(4L, beyondEnd.getTotalElements());
        assertEquals(1L, reads().history(2L, 0, 2).getTotalElements());
    }

    @Test
    void readMappingPreservesNullsSignedMeasurementsAndOverload() {
        store.insertBoot(1L, boot, received);
        insert(1L, sample(false));

        var supported = reads().findLatest(1L).orElseThrow();
        assertEquals(0, new BigDecimal("-2")
                .compareTo(supported.battery().currentA()));
        assertEquals(0, new BigDecimal("-100")
                .compareTo(supported.grid().inputPowerW()));
        assertEquals(0, new BigDecimal("130")
                .compareTo(supported.load().loadPercent()));
        assertEquals(Boolean.TRUE, supported.grid().present());
        assertEquals(received.minusSeconds(1), supported.measuredAt());
        assertEquals(received, supported.receivedAt());

        store.insertBoot(2L, boot, received);
        insert(2L, sample(true));

        var unsupported = reads().findLatest(2L).orElseThrow();
        assertNull(unsupported.measuredAt());
        assertEquals(new TelemetryRequest.Battery(null, null, null),
                unsupported.battery());
        assertEquals(new TelemetryRequest.Grid(null, null, null, null),
                unsupported.grid());
        assertEquals(new TelemetryRequest.Load(null, null, null, null),
                unsupported.load());
    }

    @Test
    void emptyReadsAndInvalidPaginationHaveExplicitResults() {
        assertTrue(reads().findLatest(1L).isEmpty());

        var empty = reads().history(1L, 0, 20);
        assertTrue(empty.isEmpty());
        assertEquals(0L, empty.getTotalElements());
        assertEquals(0, empty.getTotalPages());

        assertThrows(IllegalArgumentException.class,
                () -> reads().findLatest(0L));
        assertThrows(IllegalArgumentException.class,
                () -> reads().history(1L, -1, 20));
        assertThrows(IllegalArgumentException.class,
                () -> reads().history(1L, 0, 0));
        assertThrows(IllegalArgumentException.class,
                () -> reads().history(1L, 0, 101));
    }

    @Test
    void latestReadDoesNotSilentlyHideMultipleActiveBoots() {
        store.insertBoot(1L, boot, received);
        insert(1L, sampleFor(boot, 1L, 5000L));

        UUID unexpected = UUID.randomUUID();
        store.insertBoot(1L, unexpected, received);
        insert(1L, sampleFor(unexpected, 1L, 5000L));

        assertThrows(
                org.springframework.dao.IncorrectResultSizeDataAccessException.class,
                () -> reads().findLatest(1L));
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