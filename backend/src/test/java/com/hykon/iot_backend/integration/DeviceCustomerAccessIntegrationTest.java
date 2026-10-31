package com.hykon.iot_backend.integration;

import com.hykon.iot_backend.dto.AuthorizeCustomerRequest;
import com.hykon.iot_backend.dto.ClaimDeviceRequest;
import com.hykon.iot_backend.dto.DeviceResponse;
import com.hykon.iot_backend.entity.*;
import com.hykon.iot_backend.repository.*;
import com.hykon.iot_backend.service.*;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.web.server.ResponseStatusException;

import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ContextConfiguration(initializers = GateTestDatabaseInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DeviceCustomerAccessIntegrationTest {

    private static final String DEDICATED_TEST_DB = "hykon_iot_gate_test";

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
    private DeviceCustomerAccessRepository customerAccessRepository;

    @Autowired
    private SessionAccessGrantRepository grantRepository;

    @Autowired
    private LoginSessionService sessionService;

    @Autowired
    private AccessVerificationCoordinator coordinator;

    @Autowired
    private DeviceCustomerAccessService customerAccessService;

    @Autowired
    private DeviceQueryService deviceQueryService;

    @Autowired
    private SessionAccessPolicyService sessionAccessPolicyService;

    private JdbcTemplate jdbc;

    @BeforeAll
    void verifyDatabaseSafety() {
        jdbc = new JdbcTemplate(dataSource);

        // Explicit database safety check: NEVER run against main or unrecognized database
        String currentDb = jdbc.queryForObject("SELECT current_database()", String.class);
        assertEquals(
                DEDICATED_TEST_DB,
                currentDb,
                "Integration tests MUST run only on isolated test database '" + DEDICATED_TEST_DB + "'"
        );
    }

    private User createUser(String emailPrefix, RoleName roleName) {
        Role role = roleRepository.findByName(roleName)
                .orElseGet(() -> roleRepository.saveAndFlush(new Role(roleName)));

        User user = new User();
        user.setName("Test " + emailPrefix);
        user.setEmail(emailPrefix + "-" + UUID.randomUUID() + "@example.com");
        user.setPassword("secret123");
        user.setRole(role);
        user.setStatus("ACTIVE");

        return userRepository.saveAndFlush(user);
    }

    private static final AtomicInteger DEVICE_SEQ = new AtomicInteger(1);

    private TestFixture createFixture() {
        User companyAdmin = createUser("admin", RoleName.HYKON_ADMIN);
        User distAdmin = createUser("dist-admin", RoleName.DISTRIBUTOR_ADMIN);
        User owner = createUser("owner", RoleName.USER);

        Distributor dist = new Distributor();
        dist.setDistributorCode("DIST-" + UUID.randomUUID().toString().substring(0, 8));
        dist.setName("Integration Distributor");
        dist.setAdministrator(distAdmin);
        dist.setCreatedBy(companyAdmin);
        dist.setStatus("ACTIVE");
        dist = distributorRepository.saveAndFlush(dist);

        DistributorBranch branch = new DistributorBranch();
        branch.setDistributor(dist);
        branch.setBranchCode("BR-1");
        branch.setName("Kochi Branch");
        branch.setLocation("Ernakulam");
        branch.setStatus("ACTIVE");
        branch = branchRepository.saveAndFlush(branch);

        int seq = DEVICE_SEQ.getAndIncrement();
        String num = String.format("%06d", seq);
        String deviceCode = "halo1200-" + num;
        String serialNumber = "2610" + num;

        Device device = new Device();
        device.setDeviceCode(deviceCode);
        device.setSerialNumber(serialNumber);
        device.setSystemName("Halo System");
        device.setBranch(branch);
        device.setCreatedBy(companyAdmin);
        device.setStatus("ACTIVE");
        device.setOwner(owner);
        device = deviceRepository.saveAndFlush(device);

        DevicePurchase purchase = new DevicePurchase();
        purchase.setDevice(device);
        purchase.setCustomer(owner);
        purchase.setRecordedBy(distAdmin);
        purchase.setPurchaseReference("INV-OWNER-" + UUID.randomUUID());
        purchase.setStatus("CLAIMED");
        purchase.setClaimedAt(java.time.LocalDateTime.now());
        purchaseRepository.saveAndFlush(purchase);

        return new TestFixture(companyAdmin, distAdmin, owner, dist, branch, device);
    }

    @Test
    void migration004RunsTwiceWithoutDuplicatesOrOverwrittenHistory() {
        TestFixture fix = createFixture();

        // 1. Initial backfill from SQL script
        String backfillSql = """
                INSERT INTO device_authorized_customers (device_id, user_id, is_primary_owner, created_by_user_id, created_at)
                SELECT d.id, d.owner_user_id, TRUE, d.owner_user_id, CURRENT_TIMESTAMP
                FROM devices d
                WHERE d.owner_user_id IS NOT NULL
                ON CONFLICT (device_id, user_id) DO UPDATE
                SET is_primary_owner = TRUE;
                """;

        jdbc.execute(backfillSql);

        Long countAfterFirst = jdbc.queryForObject(
                "SELECT COUNT(*) FROM device_authorized_customers WHERE device_id = ?",
                Long.class,
                fix.device.getId()
        );
        assertEquals(1L, countAfterFirst);

        java.sql.Timestamp firstCreatedAt = jdbc.queryForObject(
                "SELECT created_at FROM device_authorized_customers WHERE device_id = ? AND user_id = ?",
                java.sql.Timestamp.class,
                fix.device.getId(),
                fix.owner.getId()
        );
        assertNotNull(firstCreatedAt);

        // 2. Second execution of migration script
        jdbc.execute(backfillSql);

        Long countAfterSecond = jdbc.queryForObject(
                "SELECT COUNT(*) FROM device_authorized_customers WHERE device_id = ?",
                Long.class,
                fix.device.getId()
        );
        assertEquals(1L, countAfterSecond, "Migration 004 re-run must not create duplicate records");

        java.sql.Timestamp secondCreatedAt = jdbc.queryForObject(
                "SELECT created_at FROM device_authorized_customers WHERE device_id = ? AND user_id = ?",
                java.sql.Timestamp.class,
                fix.device.getId(),
                fix.owner.getId()
        );
        assertEquals(firstCreatedAt, secondCreatedAt, "Idempotent backfill must not overwrite original creation history");
    }

    @Test
    void originalOwnerCountsTowardThreeLimit() {
        TestFixture fix = createFixture();

        User secondCustomer = createUser("cust2", RoleName.USER);
        User thirdCustomer = createUser("cust3", RoleName.USER);
        User fourthCustomer = createUser("cust4", RoleName.USER);

        // Authorize second customer (total: 2)
        customerAccessService.authorizeCustomer(
                fix.device.getId(),
                new AuthorizeCustomerRequest(secondCustomer.getEmail()),
                fix.companyAdmin.getEmail()
        );

        // Authorize third customer (total: 3)
        customerAccessService.authorizeCustomer(
                fix.device.getId(),
                new AuthorizeCustomerRequest(thirdCustomer.getEmail()),
                fix.companyAdmin.getEmail()
        );

        // Attempting to authorize a fourth customer must be rejected
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                customerAccessService.authorizeCustomer(
                        fix.device.getId(),
                        new AuthorizeCustomerRequest(fourthCustomer.getEmail()),
                        fix.companyAdmin.getEmail()
                )
        );
        assertTrue(ex.getMessage().contains("maximum of 3 authorized customers"));
    }

    @Test
    void concurrentAuthorizationsCannotExceedThree() throws Exception {
        TestFixture fix = createFixture();

        int candidateCount = 5;
        List<User> candidates = new ArrayList<>();
        for (int i = 0; i < candidateCount; i++) {
            candidates.add(createUser("cand" + i, RoleName.USER));
        }

        ExecutorService pool = Executors.newFixedThreadPool(candidateCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch finishLatch = new CountDownLatch(candidateCount);
        AtomicInteger successCount = new AtomicInteger(0);

        for (User cand : candidates) {
            pool.submit(() -> {
                try {
                    startLatch.await();
                    customerAccessService.authorizeCustomer(
                            fix.device.getId(),
                            new AuthorizeCustomerRequest(cand.getEmail()),
                            fix.companyAdmin.getEmail()
                    );
                    successCount.incrementAndGet();
                } catch (Exception ignored) {
                } finally {
                    finishLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        finishLatch.await(10, TimeUnit.SECONDS);
        pool.shutdown();

        // Original owner was 1, so at most 2 additional customers could succeed
        long totalAuthorized = customerAccessRepository.countByDeviceId(fix.device.getId());
        assertTrue(totalAuthorized <= 3L, "Total authorized customers must never exceed 3");
    }

    @Test
    void additionalCustomerCanVerifyAndObtainGrant() {
        TestFixture fix = createFixture();

        User secondCustomer = createUser("secondary", RoleName.USER);
        customerAccessService.authorizeCustomer(
                fix.device.getId(),
                new AuthorizeCustomerRequest(secondCustomer.getEmail()),
                fix.companyAdmin.getEmail()
        );

        LoginSession session = sessionService.create(secondCustomer);

        ClaimDeviceRequest req = new ClaimDeviceRequest();
        req.setDeviceCode(fix.device.getDeviceCode());
        req.setSerialNumber(fix.device.getSerialNumber());

        DeviceResponse response = coordinator.verifyDevice(req, secondCustomer.getEmail(), session.getId());
        assertNotNull(response);
        assertEquals(fix.device.getDeviceCode(), response.deviceCode());

        // Verify that session grant was created and populated
        SessionAccessGrant grant = grantRepository.findById(session.getId()).orElse(null);
        assertNotNull(grant);
        assertNotNull(grant.getDevice());
        assertEquals(fix.device.getId(), grant.getDevice().getId());
        assertNotNull(grant.getDeviceVerifiedAt());
    }

    @Test
    void removalBlocksSubsequentQueriesAndExistingGrantUse() {
        TestFixture fix = createFixture();

        User secondCustomer = createUser("removable", RoleName.USER);
        customerAccessService.authorizeCustomer(
                fix.device.getId(),
                new AuthorizeCustomerRequest(secondCustomer.getEmail()),
                fix.companyAdmin.getEmail()
        );

        LoginSession session = sessionService.create(secondCustomer);

        ClaimDeviceRequest req = new ClaimDeviceRequest();
        req.setDeviceCode(fix.device.getDeviceCode());
        req.setSerialNumber(fix.device.getSerialNumber());
        coordinator.verifyDevice(req, secondCustomer.getEmail(), session.getId());

        // Device query works before removal
        assertNotNull(deviceQueryService.get(fix.device.getId(), secondCustomer.getEmail()));

        // Remove the customer
        customerAccessService.removeCustomerAccess(fix.device.getId(), secondCustomer.getId(), fix.companyAdmin.getEmail());

        // Subsequent query is blocked with 404
        assertThrows(ResponseStatusException.class, () ->
                deviceQueryService.get(fix.device.getId(), secondCustomer.getEmail()));

        // Existing grant access is blocked with 403
        Authentication auth = new TestingAuthenticationToken(secondCustomer.getEmail(), "N/A", "ROLE_USER");
        ((TestingAuthenticationToken) auth).setDetails(session.getId());

        assertThrows(ResponseStatusException.class, () ->
                sessionAccessPolicyService.requireVerified(auth));
    }

    @Test
    void crossDistributorAccessIsDenied() {
        TestFixture fix = createFixture();

        User otherDistAdmin = createUser("other-dist", RoleName.DISTRIBUTOR_ADMIN);
        User customer = createUser("cust", RoleName.USER);

        Distributor otherDist = new Distributor();
        otherDist.setDistributorCode("DIST-OTHER-" + UUID.randomUUID().toString().substring(0, 8));
        otherDist.setName("Other Distributor");
        otherDist.setAdministrator(otherDistAdmin);
        otherDist.setCreatedBy(fix.companyAdmin);
        otherDist.setStatus("ACTIVE");
        distributorRepository.saveAndFlush(otherDist);

        // Other distributor admin attempting to authorize a customer on Distributor A's device must be denied
        assertThrows(AccessDeniedException.class, () ->
                customerAccessService.authorizeCustomer(
                        fix.device.getId(),
                        new AuthorizeCustomerRequest(customer.getEmail()),
                        otherDistAdmin.getEmail()
                )
        );
    }

    @Test
    void regressionOwnerAndAdditionalCustomerAccessWithMultipleMemberships() {
        TestFixture fix = createFixture();

        // Authorize two additional customers on the device (total: 3 authorized accounts)
        User secondCustomer = createUser("cust-two", RoleName.USER);
        User thirdCustomer = createUser("cust-three", RoleName.USER);

        customerAccessService.authorizeCustomer(
                fix.device.getId(),
                new AuthorizeCustomerRequest(secondCustomer.getEmail()),
                fix.companyAdmin.getEmail()
        );
        customerAccessService.authorizeCustomer(
                fix.device.getId(),
                new AuthorizeCustomerRequest(thirdCustomer.getEmail()),
                fix.companyAdmin.getEmail()
        );

        // Verify total memberships count in access table (1 primary owner + 2 additional customers = 3)
        List<DeviceCustomerAccess> memberships = customerAccessRepository.findByDeviceIdWithCustomer(fix.device.getId());
        assertEquals(3, memberships.size(), "Access table must record 3 total memberships (1 primary owner + 2 additional)");
        assertEquals(3L, customerAccessRepository.countByDeviceId(fix.device.getId()));

        long primaryOwners = memberships.stream().filter(DeviceCustomerAccess::isPrimaryOwner).count();
        long additionalMembers = memberships.stream().filter(m -> !m.isPrimaryOwner()).count();
        assertEquals(1L, primaryOwners, "Device must have exactly 1 primary owner recorded in access table");
        assertEquals(2L, additionalMembers, "Device must have exactly 2 additional customer memberships");

        assertTrue(memberships.stream().anyMatch(m -> m.getCustomer().getId().equals(fix.owner.getId()) && m.isPrimaryOwner()));
        assertTrue(memberships.stream().anyMatch(m -> m.getCustomer().getId().equals(secondCustomer.getId()) && !m.isPrimaryOwner()));
        assertTrue(memberships.stream().anyMatch(m -> m.getCustomer().getId().equals(thirdCustomer.getId()) && !m.isPrimaryOwner()));

        // Confirm that the 3-customer limit is enforced and a 4th customer cannot be added
        User fourthCustomer = createUser("cust-four", RoleName.USER);
        IllegalArgumentException limitEx = assertThrows(IllegalArgumentException.class, () ->
                customerAccessService.authorizeCustomer(
                        fix.device.getId(),
                        new AuthorizeCustomerRequest(fourthCustomer.getEmail()),
                        fix.companyAdmin.getEmail()
                )
        );
        assertTrue(limitEx.getMessage().contains("maximum of 3 authorized customers"));

        // 1. Direct repository queries must return exactly ONE distinct device (no duplication)
        Optional<Device> ownerLookup = deviceRepository.findByIdAndOwner_Id(fix.device.getId(), fix.owner.getId());
        assertTrue(ownerLookup.isPresent());
        assertEquals(fix.device.getId(), ownerLookup.get().getId());

        Optional<Device> secondaryLookup = deviceRepository.findByIdAndOwner_Id(fix.device.getId(), secondCustomer.getId());
        assertTrue(secondaryLookup.isPresent());
        assertEquals(fix.device.getId(), secondaryLookup.get().getId());

        Optional<Device> tertiaryLookup = deviceRepository.findByIdAndOwner_Id(fix.device.getId(), thirdCustomer.getId());
        assertTrue(tertiaryLookup.isPresent());
        assertEquals(fix.device.getId(), tertiaryLookup.get().getId());

        // 2. Authorized lookup must also return exactly ONE distinct device
        Optional<Device> authLookup = deviceRepository.findAuthorizedDeviceByIdAndUser(fix.device.getId(), secondCustomer.getId());
        assertTrue(authLookup.isPresent());
        assertEquals(fix.device.getId(), authLookup.get().getId());

        // 3. Device detail via service returns exactly one distinct device with all authorized emails
        DeviceResponse ownerDetail = deviceQueryService.get(fix.device.getId(), fix.owner.getEmail());
        assertNotNull(ownerDetail);
        assertEquals(fix.device.getDeviceCode(), ownerDetail.deviceCode());
        assertTrue(ownerDetail.customerEmails().contains(secondCustomer.getEmail()));
        assertTrue(ownerDetail.customerEmails().contains(thirdCustomer.getEmail()));

        DeviceResponse secondaryDetail = deviceQueryService.get(fix.device.getId(), secondCustomer.getEmail());
        assertNotNull(secondaryDetail);
        assertEquals(fix.device.getDeviceCode(), secondaryDetail.deviceCode());

        // 4. Customer list pagination and count remains exactly 1 for each customer
        Page<Device> ownerDevices = deviceRepository.findByOwner_Id(fix.owner.getId(), PageRequest.of(0, 10));
        assertEquals(1, ownerDevices.getTotalElements());
        assertEquals(1, ownerDevices.getContent().size());
        assertEquals(fix.device.getId(), ownerDevices.getContent().get(0).getId());

        Page<Device> secondaryDevices = deviceRepository.findByOwner_Id(secondCustomer.getId(), PageRequest.of(0, 10));
        assertEquals(1, secondaryDevices.getTotalElements());
        assertEquals(1, secondaryDevices.getContent().size());
        assertEquals(fix.device.getId(), secondaryDevices.getContent().get(0).getId());

        // 5. Verification coordinator succeeds without duplicate result size exceptions
        LoginSession ownerSession = sessionService.create(fix.owner);
        ClaimDeviceRequest claimReq = new ClaimDeviceRequest();
        claimReq.setDeviceCode(fix.device.getDeviceCode());
        claimReq.setSerialNumber(fix.device.getSerialNumber());

        DeviceResponse ownerVerified = coordinator.verifyDevice(claimReq, fix.owner.getEmail(), ownerSession.getId());
        assertNotNull(ownerVerified);
        assertEquals(fix.device.getDeviceCode(), ownerVerified.deviceCode());

        LoginSession secondSession = sessionService.create(secondCustomer);
        DeviceResponse secondVerified = coordinator.verifyDevice(claimReq, secondCustomer.getEmail(), secondSession.getId());
        assertNotNull(secondVerified);
        assertEquals(fix.device.getDeviceCode(), secondVerified.deviceCode());
    }

    private record TestFixture(
            User companyAdmin,
            User distAdmin,
            User owner,
            Distributor distributor,
            DistributorBranch branch,
            Device device
    ) {}
}

