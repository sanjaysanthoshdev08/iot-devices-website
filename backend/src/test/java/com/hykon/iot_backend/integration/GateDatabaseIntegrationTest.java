package com.hykon.iot_backend.integration;

import com.hykon.iot_backend.dto.ClaimDeviceRequest;
import com.hykon.iot_backend.dto.DeviceResponse;
import com.hykon.iot_backend.entity.*;
import com.hykon.iot_backend.repository.*;
import com.hykon.iot_backend.service.AccessVerificationCoordinator;
import com.hykon.iot_backend.service.LoginSessionService;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.web.server.ResponseStatusException;

import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ContextConfiguration(initializers = GateTestDatabaseInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class GateDatabaseIntegrationTest {

    @Autowired
    private DataSource dataSource;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private DistributorRepository distributorRepository;

    @Autowired
    private DistributorBranchRepository branchRepository;

    @Autowired
    private DeviceRepository deviceRepository;

    @Autowired
    private DevicePurchaseRepository purchaseRepository;

    @Autowired
    private LoginSessionService sessionService;

    @Autowired
    private AccessVerificationCoordinator coordinator;

    private JdbcTemplate jdbc;

    @BeforeAll
    void prepareTestConstraints() {
        jdbc = new JdbcTemplate(dataSource);

        assertEquals(
                "hykon_iot_gate_test",
                jdbc.queryForObject("SELECT current_database()", String.class)
        );

        // Match constraints from the saved SQL scripts that are not
        // represented by JPA relationships or column annotations.
        jdbc.execute("""
                ALTER TABLE public.session_access_grants
                ADD CONSTRAINT it_grant_session_fk
                    FOREIGN KEY (session_id)
                    REFERENCES public.login_sessions(id)
                    ON DELETE CASCADE,
                ADD CONSTRAINT it_grant_device_pair
                    CHECK (
                        (device_id IS NULL) = (device_verified_at IS NULL)
                    ),
                ADD CONSTRAINT it_grant_branch_pair
                    CHECK (
                        (branch_id IS NULL) =
                        (distributor_verified_at IS NULL)
                    ),
                ADD CONSTRAINT it_grant_has_verification
                    CHECK (device_id IS NOT NULL OR branch_id IS NOT NULL)
                """);

        jdbc.execute("""
                ALTER TABLE public.access_verification_attempts
                ADD CONSTRAINT it_attempt_user_fk
                    FOREIGN KEY (user_id)
                    REFERENCES public.users(id)
                    ON DELETE CASCADE,
                ADD CONSTRAINT it_attempt_nonnegative
                    CHECK (failed_attempts >= 0),
                ADD CONSTRAINT it_attempt_lock
                    CHECK (locked_until IS NULL OR failed_attempts > 0)
                """);
    }

    @AfterAll
    void removeExtraForeignKeys() {
        // Let Hibernate remove its test tables in its normal order.
        jdbc.execute("""
                ALTER TABLE IF EXISTS public.session_access_grants
                DROP CONSTRAINT IF EXISTS it_grant_session_fk
                """);
        jdbc.execute("""
                ALTER TABLE IF EXISTS public.access_verification_attempts
                DROP CONSTRAINT IF EXISTS it_attempt_user_fk
                """);
    }

    private User createUser(RoleName roleName) {
        Role role = roleRepository.findByName(roleName)
                .orElseGet(() ->
                        roleRepository.saveAndFlush(new Role(roleName)));

        User user = new User();
        user.setName("Gate integration test");
        user.setEmail("gate-" + UUID.randomUUID() + "@example.com");
        user.setPassword("unused-test-fixture-password");
        user.setRole(role);
        user.setStatus("ACTIVE");

        return userRepository.saveAndFlush(user);
    }

    private Fixture createFixture() {
        User companyAdmin = createUser(RoleName.HYKON_ADMIN);
        User distributorAdmin = createUser(RoleName.DISTRIBUTOR_ADMIN);
        User customer = createUser(RoleName.USER);

        Distributor distributor = new Distributor();
        distributor.setDistributorCode("DIST-" + UUID.randomUUID());
        distributor.setName("Integration distributor");
        distributor.setAdministrator(distributorAdmin);
        distributor.setCreatedBy(companyAdmin);
        distributor.setStatus("ACTIVE");
        distributor = distributorRepository.saveAndFlush(distributor);

        DistributorBranch branch = new DistributorBranch();
        branch.setDistributor(distributor);
        branch.setBranchCode("MAIN");
        branch.setName("Integration branch");
        branch.setLocation("EKM");
        branch.setStatus("ACTIVE");
        branch = branchRepository.saveAndFlush(branch);

        Device device = new Device();
        device.setDeviceCode("DEVICE-" + UUID.randomUUID());
        device.setSerialNumber("SERIAL-" + UUID.randomUUID());
        device.setSystemName("Integration test system");
        device.setBranch(branch);
        device.setCreatedBy(companyAdmin);
        device.setStatus("ACTIVE");
        device = deviceRepository.saveAndFlush(device);

        DevicePurchase purchase = new DevicePurchase();
        purchase.setDevice(device);
        purchase.setCustomer(customer);
        purchase.setRecordedBy(distributorAdmin);
        purchase.setPurchaseReference("INV-" + UUID.randomUUID());
        purchase.setStatus("ELIGIBLE");
        purchase = purchaseRepository.saveAndFlush(purchase);

        LoginSession session = sessionService.create(customer);

        return new Fixture(
                customer.getId(),
                customer.getEmail(),
                device.getId(),
                device.getDeviceCode(),
                device.getSerialNumber(),
                purchase.getId(),
                session.getId()
        );
    }

    private void assertUnclaimed(Fixture fixture) {
        assertNull(jdbc.queryForObject(
                "SELECT owner_user_id FROM public.devices WHERE id = ?",
                Long.class,
                fixture.deviceId()
        ));

        assertEquals("ELIGIBLE", jdbc.queryForObject(
                "SELECT status FROM public.device_purchases WHERE id = ?",
                String.class,
                fixture.purchaseId()
        ));

        assertNull(jdbc.queryForObject(
                "SELECT claimed_at FROM public.device_purchases WHERE id = ?",
                java.sql.Timestamp.class,
                fixture.purchaseId()
        ));

        assertEquals(0L, jdbc.queryForObject(
                """
                SELECT COUNT(*)
                FROM public.session_access_grants
                WHERE session_id = ?
                """,
                Long.class,
                fixture.sessionId()
        ));
    }

    @Test
    void grantStorageFailureRollsBackClaimButKeepsAttemptCounter() {
        Fixture fixture = createFixture();

        // A real PostgreSQL constraint forces grant persistence to fail.
        jdbc.execute(
                "ALTER TABLE public.session_access_grants "
                + "ADD CONSTRAINT it_reject_test_grant CHECK (session_id <> '"
                + fixture.sessionId()
                + "'::uuid)"
        );

        try {
            assertThrows(DataIntegrityViolationException.class,
                    () -> coordinator.verifyDevice(
                            fixture.request(true),
                            fixture.email(),
                            fixture.sessionId()
                    ));

            assertUnclaimed(fixture);

            assertEquals(1, jdbc.queryForObject(
                    """
                    SELECT failed_attempts
                    FROM public.access_verification_attempts
                    WHERE user_id = ?
                    """,
                    Integer.class,
                    fixture.customerId()
            ));
        } finally {
            jdbc.execute("""
                    ALTER TABLE public.session_access_grants
                    DROP CONSTRAINT IF EXISTS it_reject_test_grant
                    """);
        }
    }

    @Test
    void concurrentIncorrectRequestsCannotBypassFiveAttemptLimit()
            throws Exception {
        Fixture fixture = createFixture();
        List<Callable<Integer>> requests = new ArrayList<>();

        for (int index = 0; index < 8; index++) {
            requests.add(() -> {
                try {
                    coordinator.verifyDevice(
                            fixture.request(false),
                            fixture.email(),
                            fixture.sessionId()
                    );
                    return 200;
                } catch (ResponseStatusException exception) {
                    return exception.getStatusCode().value();
                } catch (IllegalArgumentException exception) {
                    assertEquals(
                            "Device verification failed",
                            exception.getMessage()
                    );
                    return 400;
                }
            });
        }

        List<Integer> statuses = runTogether(requests);

        assertEquals(5L, statuses.stream()
                .filter(status -> status == 400).count());
        assertEquals(3L, statuses.stream()
                .filter(status -> status == 429).count());

        assertEquals(5, jdbc.queryForObject(
                """
                SELECT failed_attempts
                FROM public.access_verification_attempts
                WHERE user_id = ?
                """,
                Integer.class,
                fixture.customerId()
        ));

        assertNotNull(jdbc.queryForObject(
                """
                SELECT locked_until
                FROM public.access_verification_attempts
                WHERE user_id = ?
                """,
                java.sql.Timestamp.class,
                fixture.customerId()
        ));

        assertUnclaimed(fixture);
    }

    @Test
    void concurrentValidSessionsKeepOneOwnerAndSeparateGrants()
            throws Exception {
        Fixture fixture = createFixture();

        User customer = userRepository.findByEmailWithRole(fixture.email())
                .orElseThrow();

        UUID secondSessionId = sessionService.create(customer).getId();

        List<Callable<DeviceResponse>> requests = List.of(
                () -> coordinator.verifyDevice(
                        fixture.request(true),
                        fixture.email(),
                        fixture.sessionId()
                ),
                () -> coordinator.verifyDevice(
                        fixture.request(true),
                        fixture.email(),
                        secondSessionId
                )
        );

        List<DeviceResponse> responses = runTogether(requests);

        for (DeviceResponse response : responses) {
            assertEquals(fixture.deviceId(), response.id());
            assertEquals(fixture.customerId(), response.ownerUserId());
        }

        assertEquals(fixture.customerId(), jdbc.queryForObject(
                "SELECT owner_user_id FROM public.devices WHERE id = ?",
                Long.class,
                fixture.deviceId()
        ));

        assertEquals("CLAIMED", jdbc.queryForObject(
                "SELECT status FROM public.device_purchases WHERE id = ?",
                String.class,
                fixture.purchaseId()
        ));

        assertNotNull(jdbc.queryForObject(
                "SELECT claimed_at FROM public.device_purchases WHERE id = ?",
                java.sql.Timestamp.class,
                fixture.purchaseId()
        ));

        assertEquals(2L, jdbc.queryForObject(
                """
                SELECT COUNT(*)
                FROM public.session_access_grants
                WHERE device_id = ?
                  AND session_id IN (?, ?)
                  AND device_verified_at IS NOT NULL
                """,
                Long.class,
                fixture.deviceId(),
                fixture.sessionId(),
                secondSessionId
        ));

        assertEquals(0, jdbc.queryForObject(
                """
                SELECT failed_attempts
                FROM public.access_verification_attempts
                WHERE user_id = ?
                """,
                Integer.class,
                fixture.customerId()
        ));
    }

    private <T> List<T> runTogether(List<Callable<T>> tasks)
            throws Exception {
        ExecutorService executor =
                Executors.newFixedThreadPool(tasks.size());

        CountDownLatch start = new CountDownLatch(1);

        try {
            List<Future<T>> futures = new ArrayList<>();

            for (Callable<T> task : tasks) {
                futures.add(executor.submit(() -> {
                    if (!start.await(10, TimeUnit.SECONDS)) {
                        throw new IllegalStateException(
                                "Concurrent test start timed out"
                        );
                    }
                    return task.call();
                }));
            }

            start.countDown();

            List<T> results = new ArrayList<>();

            for (Future<T> future : futures) {
                results.add(future.get(30, TimeUnit.SECONDS));
            }

            return results;
        } finally {
            executor.shutdownNow();

            if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException(
                        "Concurrent test workers did not stop"
                );
            }
        }
    }

    private record Fixture(
            Long customerId,
            String email,
            Long deviceId,
            String deviceCode,
            String serialNumber,
            Long purchaseId,
            UUID sessionId
    ) {
        private ClaimDeviceRequest request(boolean correctSerial) {
            ClaimDeviceRequest request = new ClaimDeviceRequest();
            request.setDeviceCode(deviceCode);
            request.setSerialNumber(
                    correctSerial ? serialNumber : "WRONG-SERIAL"
            );
            return request;
        }
    }
}