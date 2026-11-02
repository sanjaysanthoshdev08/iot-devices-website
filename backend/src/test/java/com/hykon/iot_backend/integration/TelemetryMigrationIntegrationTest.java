package com.hykon.iot_backend.integration;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class TelemetryMigrationIntegrationTest {

    private Connection connection;
    private final String hash = "a".repeat(64);
    private final String boot = "d40189cd-e749-433a-881d-362d78196750";

    @BeforeEach
    void prepareIsolatedSchema() throws Exception {
        String password = System.getenv("HYKON_GATE_TEST_DB_PASSWORD");
        if (password == null || password.isBlank()) {
            throw new IllegalStateException(
                    "HYKON_GATE_TEST_DB_PASSWORD is required");
        }

        connection = DriverManager.getConnection(
                "jdbc:postgresql://localhost:5432/hykon_iot_gate_test",
                "postgres", password
        );

        try (var statement = connection.createStatement();
             var result = statement.executeQuery("SELECT current_database()")) {
            assertTrue(result.next());
            assertEquals("hykon_iot_gate_test", result.getString(1),
                    "Refusing to write outside the guarded test database");
        }

        connection.setAutoCommit(false);
        String schema = "telemetry_test_" +
                UUID.randomUUID().toString().replace("-", "");

        execute("CREATE SCHEMA " + schema);
        execute("SET LOCAL search_path TO " + schema + ", pg_catalog");

        // Minimal parent fixtures test migration FKs without touching
        // existing application tables. Full application tests follow later.
        execute("CREATE TABLE users (id BIGINT PRIMARY KEY)");
        execute("CREATE TABLE devices (id BIGINT PRIMARY KEY)");
        execute("INSERT INTO users VALUES (1)");
        execute("INSERT INTO devices VALUES (1)");

        execute(Files.readString(
                Path.of("sql", "005_create_device_telemetry.sql")));
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

    private void execute(String sql) throws SQLException {
        try (var statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private long count(String table) throws SQLException {
        try (var statement = connection.createStatement();
             var result = statement.executeQuery(
                     "SELECT COUNT(*) FROM " + table)) {
            assertTrue(result.next());
            return result.getLong(1);
        }
    }

    private void expectConstraint(String sql, String state) throws Exception {
        var savepoint = connection.setSavepoint();
        try {
            SQLException failure = assertThrows(
                    SQLException.class, () -> execute(sql));
            assertEquals(state, failure.getSQLState());
        } finally {
            connection.rollback(savepoint);
            connection.releaseSavepoint(savepoint);
        }
    }

    private void createBoot() throws SQLException {
        execute("""
                INSERT INTO device_telemetry_boots
                    (device_id, boot_id, first_received_at)
                VALUES (1, '%s', CURRENT_TIMESTAMP)
                """.formatted(boot));
    }

    private String sample(
            long sequence, String current, String soc, String loadPercent
    ) {
        return """
                INSERT INTO device_telemetry_readings (
                    device_id, boot_id, sequence, schema_version,
                    received_at, uptime_ms, simulated,
                    operating_mode, payload_hash,
                    battery_current_a, battery_soc_percent,
                    grid_input_power_w, load_percent
                ) VALUES (
                    1, '%s', %d, 1, CURRENT_TIMESTAMP, 5000, TRUE,
                    'LINE', '%s', %s, %s, -100, %s
                )
                """.formatted(
                        boot, sequence, hash, current, soc, loadPercent);
    }

    @Test
    void migrationCreatesEmptyTablesWithoutDeviceCredentials() throws Exception {
        assertEquals(0, count("device_telemetry_credentials"));
        assertEquals(0, count("device_telemetry_boots"));
        assertEquals(0, count("device_telemetry_readings"));
        assertEquals(1, count("devices"));
    }

    @Test
    void onlyOneActiveCredentialPerDeviceAndRotationPreservesHistory()
            throws Exception {
        execute("""
                INSERT INTO device_telemetry_credentials
                    (key_id, device_id, secret_hash, created_by_user_id)
                VALUES ('%s', 1, '%s', 1)
                """.formatted(UUID.randomUUID(), hash));

        String replacement = """
                INSERT INTO device_telemetry_credentials
                    (key_id, device_id, secret_hash, created_by_user_id)
                VALUES ('%s', 1, '%s', 1)
                """.formatted(UUID.randomUUID(), hash);

        expectConstraint(replacement, "23505");
        execute("""
                UPDATE device_telemetry_credentials
                SET revoked_at = CURRENT_TIMESTAMP
                WHERE device_id = 1
                """);
        execute(replacement);
        assertEquals(2, count("device_telemetry_credentials"));
    }

    @Test
    void sampleIdentityAndBootIdentityAreUnique() throws Exception {
        createBoot();
        execute(sample(1, "2", "80", "50"));
        expectConstraint(sample(1, "2", "80", "50"), "23505");
        expectConstraint("""
                INSERT INTO device_telemetry_boots
                    (device_id, boot_id, first_received_at)
                VALUES (1, '%s', CURRENT_TIMESTAMP)
                """.formatted(boot), "23505");
        assertEquals(1, count("device_telemetry_readings"));
        assertEquals(1, count("device_telemetry_boots"));
    }

    @Test
    void permitsSignedValuesNullsAndActualOverload() throws Exception {
        createBoot();
        execute(sample(1, "-10", "80", "130"));
        execute(sample(2, "NULL", "NULL", "NULL"));
        assertEquals(2, count("device_telemetry_readings"));
    }

    @Test
    void rejectsInvalidCountersSocNegativeUnsignedAndNonfiniteValues()
            throws Exception {
        createBoot();
        expectConstraint(sample(0, "2", "80", "50"), "23514");
        expectConstraint(sample(1, "2", "101", "50"), "23514");
        expectConstraint(sample(1, "2", "-1", "50"), "23514");
        expectConstraint(sample(1, "2", "80", "-1"), "23514");
        expectConstraint(sample(1, "'NaN'", "80", "50"), "23514");
        expectConstraint(sample(1, "'Infinity'", "80", "50"), "23514");
        assertEquals(0, count("device_telemetry_readings"));
    }

    @Test
    void rejectsReadingsWithoutRegisteredBootAndUnknownDevices()
            throws Exception {
        expectConstraint(sample(1, "2", "80", "50"), "23503");
        expectConstraint("""
                INSERT INTO device_telemetry_boots
                    (device_id, boot_id, first_received_at)
                VALUES (999, '%s', CURRENT_TIMESTAMP)
                """.formatted(boot), "23503");
    }
}