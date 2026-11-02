package com.hykon.iot_backend.integration;

import com.hykon.iot_backend.dto.IssuedDeviceCredential;
import com.hykon.iot_backend.entity.*;
import com.hykon.iot_backend.repository.*;
import com.hykon.iot_backend.service.*;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ContextConfiguration(initializers = GateTestDatabaseInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DeviceTelemetryCredentialIntegrationTest {

    @Autowired private DataSource dataSource;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private RoleRepository roles;
    @Autowired private UserRepository users;
    @Autowired private DeviceRepository devices;
    @Autowired private LoginSessionService sessions;
    @Autowired private DeviceTelemetryCredentialService service;
    @Autowired private DeviceTelemetryCredentialStore store;

    private JdbcTemplate jdbc;
    private boolean migrationApplied;

    @BeforeAll
    void initializeTelemetryTables() throws Exception {
        jdbc = new JdbcTemplate(dataSource);
        assertSafeDatabase();

        String sql = Files.readString(
                Path.of("sql", "005_create_device_telemetry.sql"));

        new TransactionTemplate(transactionManager).executeWithoutResult(
                status -> jdbc.execute(sql));

        migrationApplied = true;
    }

    @AfterAll
    void removeOnlyTelemetryTestTables() {
        if (migrationApplied) {
            assertSafeDatabase();
            new TransactionTemplate(transactionManager).executeWithoutResult(
                    status -> {
                        jdbc.execute("DROP TABLE device_telemetry_readings");
                        jdbc.execute("DROP TABLE device_telemetry_boots");
                        jdbc.execute("DROP TABLE device_telemetry_credentials");
                    });
        }
        // @DirtiesContext closes Hibernate and removes its create-drop tables.
    }

    private void assertSafeDatabase() {
        assertEquals(
                "hykon_iot_gate_test",
                jdbc.queryForObject("SELECT current_database()", String.class),
                "Refusing to modify an unrecognized database");
    }

    private Fixture fixture() {
        Role role = roles.findByName(RoleName.HYKON_ADMIN)
                .orElseGet(() -> roles.saveAndFlush(
                        new Role(RoleName.HYKON_ADMIN)));

        User admin = new User();
        admin.setName("Credential Integration Admin");
        admin.setEmail("credential-" + UUID.randomUUID() + "@example.com");
        admin.setPassword("test-only-unused-password");
        admin.setRole(role);
        admin.setStatus("ACTIVE");
        admin = users.saveAndFlush(admin);

        Device device = new Device();
        device.setDeviceCode("credential-" + UUID.randomUUID());
        device.setSerialNumber("test-" + UUID.randomUUID());
        device.setSystemName("Credential integration fixture");
        device.setCreatedBy(admin);
        device.setStatus("ACTIVE");
        device = devices.saveAndFlush(device);

        var session = sessions.create(admin);
        var authentication = new TestingAuthenticationToken(
                admin.getEmail(), null, "ROLE_HYKON_ADMIN");
        authentication.setDetails(session.getId());

        return new Fixture(device.getId(), authentication);
    }

    private long credentialCount(long deviceId, boolean activeOnly) {
        Long count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM device_telemetry_credentials "
                        + "WHERE device_id = ?"
                        + (activeOnly ? " AND revoked_at IS NULL" : ""),
                Long.class, deviceId);
        assertNotNull(count);
        return count;
    }

    @Test
    void olderTransactionCanRevokeNewerCredential() {
        Fixture fixture = fixture();

        new TransactionTemplate(transactionManager).executeWithoutResult(
                status -> {
                    var transactionStart = jdbc.queryForObject(
                            "SELECT CURRENT_TIMESTAMP",
                            java.sql.Timestamp.class);
                    assertNotNull(transactionStart);

                    var independent =
                            new TransactionTemplate(transactionManager);
                    independent.setPropagationBehavior(
                            org.springframework.transaction.TransactionDefinition
                                    .PROPAGATION_REQUIRES_NEW);

                    var issued = independent.execute(inner ->
                            service.issueOrRotate(
                                    fixture.authentication(), fixture.deviceId()));
                    assertNotNull(issued);

                    var created = jdbc.queryForObject("""
                            SELECT created_at
                            FROM device_telemetry_credentials
                            WHERE key_id = ?
                            """, java.sql.Timestamp.class, issued.keyId());
                    assertNotNull(created);
                    assertTrue(created.after(transactionStart),
                            "Fixture must be created after outer transaction started");

                    assertEquals(1, service.revoke(
                            fixture.authentication(), fixture.deviceId()));

                    var revoked = store.findByKeyId(issued.keyId())
                            .orElseThrow().revokedAt();
                    assertNotNull(revoked);
                    assertFalse(revoked.isBefore(created.toInstant()),
                            "Revocation must not predate creation");
                });

        assertEquals(0, credentialCount(fixture.deviceId(), true));
    }
    @Test
    void rotationAndRepeatedRevocationUseRealDatabaseTransactions() {
        Fixture fixture = fixture();

        var first = service.issueOrRotate(
                fixture.authentication(), fixture.deviceId());
        var second = service.issueOrRotate(
                fixture.authentication(), fixture.deviceId());

        assertNotEquals(first.keyId(), second.keyId());
        assertNotEquals(first.secret(), second.secret());
        assertNotNull(store.findByKeyId(first.keyId())
                .orElseThrow().revokedAt());
        assertNull(store.findByKeyId(second.keyId())
                .orElseThrow().revokedAt());

        assertEquals(2, credentialCount(fixture.deviceId(), false));
        assertEquals(1, credentialCount(fixture.deviceId(), true));

        assertEquals(1, service.revoke(
                fixture.authentication(), fixture.deviceId()));
        assertEquals(0, service.revoke(
                fixture.authentication(), fixture.deviceId()));
        assertEquals(0, credentialCount(fixture.deviceId(), true));
    }

    @Test
    void failedInsertionRollsBackRevocationOfPreviousCredential() {
        Fixture fixture = fixture();
        var original = service.issueOrRotate(
                fixture.authentication(), fixture.deviceId());

        assertSafeDatabase();

        // Force only this fixture's INSERT to fail in PostgreSQL.
        // UPDATE/revocation remains possible, so rollback is actually tested.
        jdbc.execute("""
                CREATE FUNCTION telemetry_test_reject_insert()
                RETURNS trigger LANGUAGE plpgsql AS $$
                BEGIN
                    RAISE EXCEPTION 'Simulated credential insertion failure';
                END;
                $$
                """);

        try {
            jdbc.execute("""
                    CREATE TRIGGER telemetry_test_insert_failure
                    BEFORE INSERT ON device_telemetry_credentials
                    FOR EACH ROW
                    WHEN (NEW.device_id = %d)
                    EXECUTE FUNCTION telemetry_test_reject_insert()
                    """.formatted(fixture.deviceId()));

            assertThrows(org.springframework.dao.DataAccessException.class,
                    () -> service.issueOrRotate(
                            fixture.authentication(), fixture.deviceId()));

            assertNull(store.findByKeyId(original.keyId())
                    .orElseThrow().revokedAt(),
                    "Previous credential must remain active after rollback");
            assertEquals(1, credentialCount(fixture.deviceId(), false));
            assertEquals(1, credentialCount(fixture.deviceId(), true));
        } finally {
            jdbc.execute("""
                    DROP TRIGGER IF EXISTS telemetry_test_insert_failure
                    ON device_telemetry_credentials
                    """);
            jdbc.execute("DROP FUNCTION telemetry_test_reject_insert()");
        }
    }

    @Test
    void concurrentRotationsSerializeOnDeviceAndLeaveOneActiveCredential()
            throws Exception {
        Fixture fixture = fixture();
        var original = service.issueOrRotate(
                fixture.authentication(), fixture.deviceId());

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        var futures = new java.util.ArrayList<Future<IssuedDeviceCredential>>();

        try {
            // Hold the real device lock while both workers begin.
            new TransactionTemplate(transactionManager).executeWithoutResult(
                    status -> {
                        devices.findByIdForUpdate(fixture.deviceId())
                                .orElseThrow();

                        for (int i = 0; i < 2; i++) {
                            futures.add(pool.submit(() -> {
                                ready.countDown();
                                return service.issueOrRotate(
                                        fixture.authentication(),
                                        fixture.deviceId());
                            }));
                        }

                        try {
                            assertTrue(ready.await(5, TimeUnit.SECONDS),
                                    "Both rotation workers must start");
                        } catch (InterruptedException exception) {
                            Thread.currentThread().interrupt();
                            throw new IllegalStateException(exception);
                        }
                    });

            var first = futures.get(0).get(15, TimeUnit.SECONDS);
            var second = futures.get(1).get(15, TimeUnit.SECONDS);

            assertNotEquals(first.keyId(), second.keyId());
            assertNotNull(store.findByKeyId(original.keyId())
                    .orElseThrow().revokedAt());

            long activeReturned = List.of(first, second).stream()
                    .filter(issued -> store.findByKeyId(issued.keyId())
                            .orElseThrow().revokedAt() == null)
                    .count();

            assertEquals(1, activeReturned);
            assertEquals(3, credentialCount(fixture.deviceId(), false));
            assertEquals(1, credentialCount(fixture.deviceId(), true));
        } finally {
            pool.shutdownNow();
            assertTrue(pool.awaitTermination(20, TimeUnit.SECONDS),
                    "Rotation workers must stop before database cleanup");
        }
    }

    private record Fixture(
            Long deviceId,
            TestingAuthenticationToken authentication
    ) {}
}