package com.hykon.iot_backend.integration;

import com.hykon.iot_backend.dto.IssuedDeviceCredential;
import com.hykon.iot_backend.dto.TelemetryRequest;
import com.hykon.iot_backend.entity.*;
import com.hykon.iot_backend.repository.*;
import com.hykon.iot_backend.service.*;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.util.UUID;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ContextConfiguration(initializers = GateTestDatabaseInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DeviceTelemetryIngestionIntegrationTest {

    @Autowired private DataSource dataSource;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private RoleRepository roles;
    @Autowired private UserRepository users;
    @Autowired private DeviceRepository devices;
    @Autowired private LoginSessionService sessions;
    @Autowired private DeviceTelemetryCredentialService credentials;
    @Autowired private DeviceTelemetryIngestionService ingestion;
    @Autowired private DeviceTelemetryStore store;

    private JdbcTemplate jdbc;
    private boolean migrationApplied;

    @BeforeAll
    void initializeTelemetryTables() throws Exception {
        jdbc = new JdbcTemplate(dataSource);
        assertSafeDatabase();

        String sql = Files.readString(
                Path.of("sql", "005_create_device_telemetry.sql"));
        new TransactionTemplate(transactionManager)
                .executeWithoutResult(status -> jdbc.execute(sql));
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
    }

    private void assertSafeDatabase() {
        assertEquals("hykon_iot_gate_test",
                jdbc.queryForObject("SELECT current_database()", String.class),
                "Refusing to modify an unrecognized database");
    }

    private Fixture fixture() {
        Role role = roles.findByName(RoleName.HYKON_ADMIN)
                .orElseGet(() -> roles.saveAndFlush(
                        new Role(RoleName.HYKON_ADMIN)));

        User admin = new User();
        admin.setName("Ingestion Integration Admin");
        admin.setEmail("ingestion-" + UUID.randomUUID() + "@example.com");
        admin.setPassword("test-only-unused-password");
        admin.setRole(role);
        admin.setStatus("ACTIVE");
        admin = users.saveAndFlush(admin);

        Device device = new Device();
        device.setDeviceCode("ingestion-" + UUID.randomUUID());
        device.setSerialNumber("test-" + UUID.randomUUID());
        device.setSystemName("Ingestion integration fixture");
        device.setCreatedBy(admin);
        device.setStatus("ACTIVE");
        device = devices.saveAndFlush(device);

        var session = sessions.create(admin);
        var authentication = new TestingAuthenticationToken(
                admin.getEmail(), null, "ROLE_HYKON_ADMIN");
        authentication.setDetails(session.getId());

        var issued = credentials.issueOrRotate(authentication, device.getId());
        return new Fixture(
                device.getId(), device.getDeviceCode(), authentication, issued);
    }

    private TelemetryRequest sample(
            Fixture fixture, UUID boot, long sequence, long uptime,
            String voltage
    ) {
        return new TelemetryRequest(
                1, fixture.deviceCode(), boot, sequence, null, uptime,
                true, TelemetryRequest.OperatingMode.LINE,
                new TelemetryRequest.Battery(
                        new BigDecimal(voltage),
                        new BigDecimal("-2"), new BigDecimal("80")),
                new TelemetryRequest.Grid(
                        true, new BigDecimal("230"), new BigDecimal("50"),
                        new BigDecimal("750")),
                new TelemetryRequest.Load(
                        new BigDecimal("230"), new BigDecimal("2.61"),
                        new BigDecimal("600"), new BigDecimal("50")));
    }

    private DeviceTelemetryIngestionService.Receipt send(
            Fixture fixture, TelemetryRequest request
    ) {
        return ingestion.ingest(
                fixture.credential().keyId().toString(),
                fixture.credential().secret(), request);
    }

    private long count(String table, long deviceId) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM " + table + " WHERE device_id = ?",
                Long.class, deviceId);
    }

    private void assertStatus(HttpStatus status, Runnable action) {
        var error = assertThrows(ResponseStatusException.class, action::run);
        assertEquals(status, error.getStatusCode());
    }

    @Test
    void equivalentRetryKeepsOriginalRowAndChangedRetryIsRejected() {
        var fixture = fixture();
        UUID boot = UUID.randomUUID();

        var first = send(fixture,
                sample(fixture, boot, 1L, 5000L, "51.2"));

        Timestamp received = jdbc.queryForObject("""
                SELECT received_at FROM device_telemetry_readings WHERE id = ?
                """, Timestamp.class, first.readingId());
        assertNotNull(received);

        var retry = send(fixture,
                sample(fixture, boot, 1L, 5000L, "51.200"));

        assertFalse(first.duplicate());
        assertTrue(retry.duplicate());
        assertEquals(first.readingId(), retry.readingId());
        assertEquals(received.toInstant(), retry.receivedAt());

        assertStatus(HttpStatus.CONFLICT, () -> send(fixture,
                sample(fixture, boot, 1L, 5000L, "52")));

        assertEquals(1L,
                count("device_telemetry_readings", fixture.deviceId()));
        assertEquals(1L,
                count("device_telemetry_boots", fixture.deviceId()));
    }

    @Test
    void sequenceOrderingAndBootRetirementPreserveLatestAcceptedSample() {
        var fixture = fixture();
        UUID firstBoot = UUID.randomUUID();
        UUID nextBoot = UUID.randomUUID();

        send(fixture, sample(fixture, firstBoot, 1L, 5000L, "51"));
        var newest = send(fixture,
                sample(fixture, firstBoot, 3L, 15000L, "52"));

        assertStatus(HttpStatus.CONFLICT, () -> send(fixture,
                sample(fixture, firstBoot, 2L, 10000L, "50")));
        assertStatus(HttpStatus.CONFLICT, () -> send(fixture,
                sample(fixture, firstBoot, 4L, 14000L, "50")));

        assertEquals(newest.readingId(),
                store.findLastSample(fixture.deviceId(), firstBoot)
                        .orElseThrow().id());

        var replacement = send(fixture, withMeasuredAt(
                sample(fixture, nextBoot, 1L, 5000L, "53"),
                store.findLastSample(fixture.deviceId(), firstBoot)
                        .orElseThrow().receivedAt().plusMillis(1)));

        assertNotNull(store.findBoot(fixture.deviceId(), firstBoot)
                .orElseThrow().retiredAt());
        assertEquals(nextBoot,
                store.findActiveBoot(fixture.deviceId()).orElseThrow().bootId());

        assertStatus(HttpStatus.CONFLICT, () -> send(fixture,
                sample(fixture, firstBoot, 4L, 20000L, "54")));

        // An unchanged historical retry is acknowledged without reactivating it.
        assertTrue(send(fixture,
                sample(fixture, firstBoot, 1L, 5000L, "51")).duplicate());

        assertEquals(nextBoot,
                store.findActiveBoot(fixture.deviceId()).orElseThrow().bootId());
        assertEquals(replacement.readingId(),
                store.findLastSample(fixture.deviceId(), nextBoot)
                        .orElseThrow().id());
        assertEquals(3L,
                count("device_telemetry_readings", fixture.deviceId()));
    }

    @Test
    void concurrentIdenticalRequestsCreateOneReadingAndOneBoot()
            throws Exception {
        var fixture = fixture();
        var request = sample(
                fixture, UUID.randomUUID(), 1L, 5000L, "51.2");
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        var futures = new java.util.ArrayList<
                Future<DeviceTelemetryIngestionService.Receipt>>();

        try {
            new TransactionTemplate(transactionManager).executeWithoutResult(
                    status -> {
                        devices.findByIdForUpdate(fixture.deviceId())
                                .orElseThrow();

                        for (int index = 0; index < 2; index++) {
                            futures.add(pool.submit(() -> {
                                ready.countDown();
                                return send(fixture, request);
                            }));
                        }

                        try {
                            assertTrue(ready.await(5, TimeUnit.SECONDS));
                        } catch (InterruptedException exception) {
                            Thread.currentThread().interrupt();
                            throw new IllegalStateException(exception);
                        }
                    });

            var first = futures.get(0).get(15, TimeUnit.SECONDS);
            var second = futures.get(1).get(15, TimeUnit.SECONDS);

            assertEquals(first.readingId(), second.readingId());
            assertNotEquals(first.duplicate(), second.duplicate(),
                    "Exactly one request inserts and the other is a retry");
            assertEquals(1L,
                    count("device_telemetry_readings", fixture.deviceId()));
            assertEquals(1L,
                    count("device_telemetry_boots", fixture.deviceId()));
        } finally {
            pool.shutdownNow();
            assertTrue(pool.awaitTermination(20, TimeUnit.SECONDS));
        }
    }

    @Test
    void failedReadingInsertionRollsBackNewBootAndKeepsPreviousBootActive() {
        var fixture = fixture();
        UUID originalBoot = UUID.randomUUID();
        UUID replacement = UUID.randomUUID();
        send(fixture,
                sample(fixture, originalBoot, 1L, 5000L, "51"));

        assertSafeDatabase();
        jdbc.execute("""
                CREATE FUNCTION telemetry_ingestion_test_reject()
                RETURNS trigger LANGUAGE plpgsql AS $$
                BEGIN
                    RAISE EXCEPTION 'Simulated reading insertion failure';
                END;
                $$
                """);

        try {
            jdbc.execute("""
                    CREATE TRIGGER telemetry_ingestion_test_failure
                    BEFORE INSERT ON device_telemetry_readings
                    FOR EACH ROW
                    WHEN (NEW.device_id = %d)
                    EXECUTE FUNCTION telemetry_ingestion_test_reject()
                    """.formatted(fixture.deviceId()));

            var replacementRequest = withMeasuredAt(
                    sample(fixture, replacement, 1L, 5000L, "52"),
                    store.findLastSample(fixture.deviceId(), originalBoot)
                            .orElseThrow().receivedAt().plusMillis(1));

            assertThrows(DataAccessException.class,
                    () -> send(fixture, replacementRequest));

            assertTrue(store.findBoot(
                    fixture.deviceId(), replacement).isEmpty());
            assertNull(store.findBoot(fixture.deviceId(), originalBoot)
                    .orElseThrow().retiredAt());
            assertEquals(originalBoot, store.findActiveBoot(
                    fixture.deviceId()).orElseThrow().bootId());
            assertEquals(1L,
                    count("device_telemetry_readings", fixture.deviceId()));
            assertEquals(1L,
                    count("device_telemetry_boots", fixture.deviceId()));
        } finally {
            jdbc.execute("""
                    DROP TRIGGER IF EXISTS telemetry_ingestion_test_failure
                    ON device_telemetry_readings
                    """);
            jdbc.execute("DROP FUNCTION telemetry_ingestion_test_reject()");
        }
    }

    @Test
    void revocationWhileRequestWaitsForDeviceLockPreventsItsWrite()
            throws Exception {
        var fixture = fixture();
        var request = sample(
                fixture, UUID.randomUUID(), 1L, 5000L, "51");
        ExecutorService pool = Executors.newSingleThreadExecutor();
        CompletableFuture<Integer> workerPid = new CompletableFuture<>();
        var futureHolder = new java.util.ArrayList<Future<
                DeviceTelemetryIngestionService.Receipt>>();

        try {
            new TransactionTemplate(transactionManager).executeWithoutResult(
                    status -> {
                        devices.findByIdForUpdate(fixture.deviceId())
                                .orElseThrow();

                        futureHolder.add(pool.submit(() ->
                                new TransactionTemplate(transactionManager)
                                        .execute(workerStatus -> {
                                            workerPid.complete(jdbc.queryForObject(
                                                    "SELECT pg_backend_pid()",
                                                    Integer.class));
                                            return send(fixture, request);
                                        })));

                        try {
                            int pid = workerPid.get(5, TimeUnit.SECONDS);
                            long deadline = System.nanoTime()
                                    + TimeUnit.SECONDS.toNanos(5);
                            boolean blocked = false;

                            while (System.nanoTime() < deadline) {
                                blocked = Boolean.TRUE.equals(
                                        jdbc.queryForObject("""
                                                SELECT cardinality(
                                                    pg_blocking_pids(?)) > 0
                                                """, Boolean.class, pid));
                                if (blocked) {
                                    break;
                                }
                                Thread.sleep(25);
                            }

                            assertTrue(blocked,
                                    "Ingestion must reach the held device lock");

                            assertEquals(1, credentials.revoke(
                                    fixture.authentication(),
                                    fixture.deviceId()));
                        } catch (InterruptedException exception) {
                            Thread.currentThread().interrupt();
                            throw new IllegalStateException(exception);
                        } catch (ExecutionException | TimeoutException exception) {
                            throw new IllegalStateException(exception);
                        }
                    });

            ExecutionException failure = assertThrows(
                    ExecutionException.class,
                    () -> futureHolder.get(0).get(15, TimeUnit.SECONDS));
            var rejected = assertInstanceOf(
                    ResponseStatusException.class, failure.getCause());
            assertEquals(HttpStatus.UNAUTHORIZED, rejected.getStatusCode());

            assertEquals(0L,
                    count("device_telemetry_readings", fixture.deviceId()));
            assertEquals(0L,
                    count("device_telemetry_boots", fixture.deviceId()));
        } finally {
            pool.shutdownNow();
            assertTrue(pool.awaitTermination(20, TimeUnit.SECONDS));
        }
    }

    private TelemetryRequest withMeasuredAt(
            TelemetryRequest request, java.time.Instant measuredAt
    ) {
        return new TelemetryRequest(
                request.schemaVersion(), request.deviceCode(), request.bootId(),
                request.sequence(), measuredAt, request.uptimeMs(),
                request.simulated(), request.operatingMode(),
                request.battery(), request.grid(), request.load());
    }

    @Test
    void unknownDelayedBootCannotReplaceCurrentBootWithoutFreshTime() {
        var fixture = fixture();
        UUID originalBoot = UUID.randomUUID();
        UUID replacementBoot = UUID.randomUUID();

        send(fixture,
                sample(fixture, originalBoot, 1L, 5000L, "51"));

        var previous = store.findLastSample(
                fixture.deviceId(), originalBoot).orElseThrow();
        var replacement = sample(
                fixture, replacementBoot, 1L, 5000L, "52");

        assertStatus(HttpStatus.CONFLICT,
                () -> send(fixture, replacement));
        assertStatus(HttpStatus.CONFLICT, () -> send(fixture,
                withMeasuredAt(replacement, previous.receivedAt())));
        assertStatus(HttpStatus.CONFLICT, () -> send(fixture,
                withMeasuredAt(replacement,
                        previous.receivedAt().minusSeconds(1))));

        assertTrue(store.findBoot(
                fixture.deviceId(), replacementBoot).isEmpty());
        assertEquals(originalBoot, store.findActiveBoot(
                fixture.deviceId()).orElseThrow().bootId());
        assertEquals(1L,
                count("device_telemetry_readings", fixture.deviceId()));

        var accepted = send(fixture, withMeasuredAt(
                replacement, previous.receivedAt().plusMillis(1)));

        assertFalse(accepted.duplicate());
        assertEquals(replacementBoot, store.findActiveBoot(
                fixture.deviceId()).orElseThrow().bootId());
        assertNotNull(store.findBoot(fixture.deviceId(), originalBoot)
                .orElseThrow().retiredAt());
        assertEquals(2L,
                count("device_telemetry_readings", fixture.deviceId()));
    }

    private record Fixture(
            Long deviceId,
            String deviceCode,
            TestingAuthenticationToken authentication,
            IssuedDeviceCredential credential
    ) {}
}