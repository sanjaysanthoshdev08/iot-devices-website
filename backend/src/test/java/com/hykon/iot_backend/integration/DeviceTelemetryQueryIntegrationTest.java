package com.hykon.iot_backend.integration;

import com.hykon.iot_backend.dto.*;
import com.hykon.iot_backend.entity.*;
import com.hykon.iot_backend.repository.*;
import com.hykon.iot_backend.service.*;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
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
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ContextConfiguration(initializers = GateTestDatabaseInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DeviceTelemetryQueryIntegrationTest {

    private static final AtomicInteger DEVICE_NUMBER =
            new AtomicInteger(60001);

    @Autowired private DataSource dataSource;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private RoleRepository roles;
    @Autowired private UserRepository users;
    @Autowired private DistributorRepository distributors;
    @Autowired private DistributorBranchRepository branches;
    @Autowired private DeviceRepository devices;
    @Autowired private DevicePurchaseRepository purchases;
    @Autowired private DeviceCustomerAccessRepository memberships;
    @Autowired private LoginSessionService sessions;
    @Autowired private AccessVerificationCoordinator coordinator;
    @Autowired private DeviceCustomerAccessService customerAccess;
    @Autowired private DeviceTelemetryCredentialService credentials;
    @Autowired private DeviceTelemetryIngestionService ingestion;
    @Autowired private DeviceTelemetryQueryService queries;

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

    private User user(String prefix, RoleName roleName) {
        Role role = roles.findByName(roleName).orElseGet(
                () -> roles.saveAndFlush(new Role(roleName)));

        User user = new User();
        user.setName("Telemetry " + prefix);
        user.setEmail(prefix + "-" + UUID.randomUUID() + "@example.com");
        user.setPassword("test-only-unused-password");
        user.setRole(role);
        user.setStatus("ACTIVE");
        return users.saveAndFlush(user);
    }

    private TestingAuthenticationToken login(User user) {
        var session = sessions.create(user);
        var authentication = new TestingAuthenticationToken(
                user.getEmail(), null, "ROLE_" + user.getRole().getName());
        authentication.setDetails(session.getId());
        return authentication;
    }

    private void verify(
            Fixture fixture, TestingAuthenticationToken authentication
    ) {
        ClaimDeviceRequest request = new ClaimDeviceRequest();
        request.setDeviceCode(fixture.device().getDeviceCode());
        request.setSerialNumber(fixture.device().getSerialNumber());

        coordinator.verifyDevice(
                request, authentication.getName(),
                (UUID) authentication.getDetails());
    }

    private TestingAuthenticationToken verifiedUser(
            Fixture fixture, User user
    ) {
        var authentication = login(user);
        verify(fixture, authentication);
        return authentication;
    }

    private Fixture fixture() {
        User admin = user("company-admin", RoleName.HYKON_ADMIN);
        User distributorAdmin = user(
                "distributor-admin", RoleName.DISTRIBUTOR_ADMIN);
        User owner = user("owner", RoleName.USER);

        Distributor distributor = new Distributor();
        distributor.setDistributorCode("TQ-" + UUID.randomUUID());
        distributor.setName("Telemetry Query Distributor");
        distributor.setAdministrator(distributorAdmin);
        distributor.setCreatedBy(admin);
        distributor.setStatus("ACTIVE");
        distributor = distributors.saveAndFlush(distributor);

        DistributorBranch branch = new DistributorBranch();
        branch.setDistributor(distributor);
        branch.setBranchCode("MAIN");
        branch.setName("Telemetry Branch");
        branch.setLocation("Ernakulam");
        branch.setStatus("ACTIVE");
        branch = branches.saveAndFlush(branch);

        String suffix = String.format("%06d", DEVICE_NUMBER.getAndIncrement());

        Device device = new Device();
        device.setDeviceCode("halo1200-" + suffix);
        device.setSerialNumber("2611" + suffix);
        device.setSystemName("Telemetry Query Fixture");
        device.setModel("HALO1200");
        device.setBranch(branch);
        device.setCreatedBy(admin);
        device.setOwner(owner);
        device.setStatus("ACTIVE");
        device = devices.saveAndFlush(device);

        DevicePurchase purchase = new DevicePurchase();
        purchase.setDevice(device);
        purchase.setCustomer(owner);
        purchase.setRecordedBy(distributorAdmin);
        purchase.setPurchaseReference("TQ-INV-" + UUID.randomUUID());
        purchase.setStatus("CLAIMED");
        purchase.setClaimedAt(LocalDateTime.now());
        purchases.saveAndFlush(purchase);

        return new Fixture(admin, owner, device, login(admin), distributorAdmin, distributor, branch);
    }

    private void seedReadings(Fixture fixture) {
        var issued = credentials.issueOrRotate(
                fixture.adminAuthentication(), fixture.device().getId());
        UUID boot = UUID.randomUUID();

        for (long sequence = 1; sequence <= 2; sequence++) {
            var request = new TelemetryRequest(
                    1, fixture.device().getDeviceCode(), boot,
                    sequence, null, sequence * 5000L,
                    true, TelemetryRequest.OperatingMode.LINE,
                    new TelemetryRequest.Battery(
                            new BigDecimal("51.2"),
                            new BigDecimal("-2"), new BigDecimal("80")),
                    new TelemetryRequest.Grid(
                            true, new BigDecimal("230"),
                            new BigDecimal("50"), new BigDecimal("750")),
                    new TelemetryRequest.Load(
                            new BigDecimal("230"), new BigDecimal("2.61"),
                            new BigDecimal("600"), new BigDecimal("50")));

            ingestion.ingest(
                    issued.keyId().toString(), issued.secret(), request);
        }
    }

    private void assertBothDenied(
            Fixture fixture, TestingAuthenticationToken authentication,
            HttpStatus expected
    ) {
        long deviceId = fixture.device().getId();

        var latest = assertThrows(ResponseStatusException.class,
                () -> queries.latest(deviceId, authentication));
        assertEquals(expected, latest.getStatusCode());

        var history = assertThrows(ResponseStatusException.class,
                () -> queries.history(deviceId, 0, 1, authentication));
        assertEquals(expected, history.getStatusCode());
    }

    @Test
    void ownerAndBothAdditionalCustomersReadOneDeviceWithExactHistoryTotals() {
        var fixture = fixture();
        User second = user("second", RoleName.USER);
        User third = user("third", RoleName.USER);

        for (User customer : List.of(second, third)) {
            customerAccess.authorizeCustomer(
                    fixture.device().getId(),
                    new AuthorizeCustomerRequest(customer.getEmail()),
                    fixture.admin().getEmail());
        }

        seedReadings(fixture);

        var membershipRows = memberships.findByDeviceIdWithCustomer(
                fixture.device().getId());
        assertEquals(3, membershipRows.size());
        assertEquals(3L, memberships.countByDeviceId(fixture.device().getId()));
        assertEquals(1L, membershipRows.stream()
                .filter(DeviceCustomerAccess::isPrimaryOwner).count());
        assertEquals(3L, membershipRows.stream()
                .map(value -> value.getCustomer().getId()).distinct().count());

        for (User customer : List.of(fixture.owner(), second, third)) {
            var authentication = verifiedUser(fixture, customer);
            var latest = queries.latest(
                    fixture.device().getId(), authentication);

            assertNotEquals(TelemetryLatestResponse.ConnectionStatus.NO_DATA,
                    latest.status());
            assertNotNull(latest.reading());
            assertEquals(fixture.device().getId().longValue(),
                    latest.reading().deviceId());
            assertEquals(2L, latest.reading().sequence());

            var first = queries.history(
                    fixture.device().getId(), 0, 1, authentication);
            var last = queries.history(
                    fixture.device().getId(), 1, 1, authentication);

            assertEquals(2L, first.totalElements());
            assertEquals(2, first.totalPages());
            assertEquals(1, first.content().size());
            assertEquals(2L, first.content().get(0).sequence());
            assertEquals(2L, last.totalElements());
            assertEquals(1, last.content().size());
            assertEquals(1L, last.content().get(0).sequence());
        }
    }

    @Test
    void removalRevokesExistingTelemetryAccessAndRestorationAllowsItAgain() {
        var fixture = fixture();
        User additional = user("removable", RoleName.USER);
        customerAccess.authorizeCustomer(
                fixture.device().getId(),
                new AuthorizeCustomerRequest(additional.getEmail()),
                fixture.admin().getEmail());

        seedReadings(fixture);
        var authentication = verifiedUser(fixture, additional);
        assertNotNull(queries.latest(
                fixture.device().getId(), authentication).reading());

        customerAccess.removeCustomerAccess(
                fixture.device().getId(), additional.getId(),
                fixture.admin().getEmail());

        assertBothDenied(fixture, authentication, HttpStatus.FORBIDDEN);

        customerAccess.authorizeCustomer(
                fixture.device().getId(),
                new AuthorizeCustomerRequest(additional.getEmail()),
                fixture.admin().getEmail());
        verify(fixture, authentication);

        assertNotNull(queries.latest(
                fixture.device().getId(), authentication).reading());
        assertEquals(2L, queries.history(
                fixture.device().getId(), 0, 20, authentication).totalElements());
        assertEquals(2L, memberships.countByDeviceId(fixture.device().getId()));
    }

    @Test
    void verifiedCustomerCannotReadAnotherDevicesTelemetry() {
        var own = fixture();
        var other = fixture();
        seedReadings(own);
        seedReadings(other);

        var authentication = verifiedUser(own, own.owner());

        assertNotNull(queries.latest(
                own.device().getId(), authentication).reading());
        assertBothDenied(other, authentication, HttpStatus.NOT_FOUND);
    }

    @Test
    void unverifiedAndDisabledCustomersCannotReadTelemetry() {
        var fixture = fixture();
        seedReadings(fixture);

        assertBothDenied(
                fixture, login(fixture.owner()), HttpStatus.FORBIDDEN);

        var verified = verifiedUser(fixture, fixture.owner());
        assertNotNull(queries.latest(
                fixture.device().getId(), verified).reading());

        User account = users.findById(fixture.owner().getId()).orElseThrow();
        account.setStatus("INACTIVE");
        users.saveAndFlush(account);

        assertBothDenied(fixture, verified, HttpStatus.FORBIDDEN);
    }

    @Test
    void hykonAdministratorCanReadAcrossDistributorsWithCorrectDeviceTotals() {
        var first = fixture();
        var second = fixture();
        seedReadings(first);
        seedReadings(second);

        for (Fixture target : List.of(first, second)) {
            var latest = queries.latest(
                    target.device().getId(), first.adminAuthentication());
            assertNotNull(latest.reading());
            assertEquals(target.device().getId().longValue(),
                    latest.reading().deviceId());

            var history = queries.history(
                    target.device().getId(), 0, 20,
                    first.adminAuthentication());
            assertEquals(2L, history.totalElements());
            assertEquals(2, history.content().size());
            assertTrue(history.content().stream().allMatch(
                    value -> value.deviceId() == target.device().getId()));
        }
    }

    private TestingAuthenticationToken verifiedDistributor(Fixture fixture) {
        var authentication = login(fixture.distributorAdmin());

        var request = new DistributorAccessRequest(
                fixture.distributor().getName(),
                fixture.branch().getName(),
                fixture.branch().getLocation());
        request.setDistributorCode(
                fixture.distributor().getDistributorCode());
        request.setBranchCode(fixture.branch().getBranchCode());

        coordinator.verifyDistributor(
                request, authentication.getName(),
                (UUID) authentication.getDetails());

        return authentication;
    }

    @Test
    void verifiedDistributorReadsOwnTelemetryButNotAnotherDistributor() {
        var own = fixture();
        var other = fixture();
        seedReadings(own);
        seedReadings(other);

        assertBothDenied(
                own, login(own.distributorAdmin()), HttpStatus.FORBIDDEN);

        var authentication = verifiedDistributor(own);

        var latest = queries.latest(own.device().getId(), authentication);
        assertNotNull(latest.reading());
        assertEquals(own.device().getId().longValue(),
                latest.reading().deviceId());
        assertEquals(2L, latest.reading().sequence());

        var history = queries.history(
                own.device().getId(), 0, 1, authentication);
        assertEquals(2L, history.totalElements());
        assertEquals(2, history.totalPages());
        assertEquals(1, history.content().size());
        assertEquals(own.device().getId().longValue(),
                history.content().get(0).deviceId());

        assertBothDenied(other, authentication, HttpStatus.NOT_FOUND);
    }

    @Test
    void reassignedDistributorAdministratorLosesExistingTelemetryGrant() {
        var fixture = fixture();
        seedReadings(fixture);
        var authentication = verifiedDistributor(fixture);

        assertNotNull(queries.latest(
                fixture.device().getId(), authentication).reading());

        User replacement = user(
                "replacement-distributor-admin", RoleName.DISTRIBUTOR_ADMIN);
        Distributor distributor = distributors.findById(
                fixture.distributor().getId()).orElseThrow();
        distributor.setAdministrator(replacement);
        distributors.saveAndFlush(distributor);

        assertBothDenied(fixture, authentication, HttpStatus.FORBIDDEN);

        // The readings remain available to the company administrator.
        assertEquals(2L, queries.history(
                fixture.device().getId(), 0, 20,
                fixture.adminAuthentication()).totalElements());
    }

    @Test
    void disabledBranchRevokesCustomerAndDistributorTelemetryGrants() {
        var fixture = fixture();
        seedReadings(fixture);
        var distributorAuthentication = verifiedDistributor(fixture);
        var ownerAuthentication = verifiedUser(fixture, fixture.owner());

        assertNotNull(queries.latest(
                fixture.device().getId(), distributorAuthentication).reading());
        assertNotNull(queries.latest(
                fixture.device().getId(), ownerAuthentication).reading());

        DistributorBranch branch = branches.findById(
                fixture.branch().getId()).orElseThrow();
        branch.setStatus("INACTIVE");
        branches.saveAndFlush(branch);

        assertBothDenied(
                fixture, distributorAuthentication, HttpStatus.FORBIDDEN);
        assertBothDenied(
                fixture, ownerAuthentication, HttpStatus.FORBIDDEN);
    }

    private record Fixture(
            User admin,
            User owner,
            Device device,
            TestingAuthenticationToken adminAuthentication,
            User distributorAdmin,
            Distributor distributor,
            DistributorBranch branch
    ) {}
}