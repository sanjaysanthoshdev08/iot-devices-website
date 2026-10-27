package com.hykon.iot_backend.service;

import com.hykon.iot_backend.dto.*;
import com.hykon.iot_backend.entity.*;
import com.hykon.iot_backend.exception.InvalidTokenException;
import com.hykon.iot_backend.repository.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SessionAccessVerificationServiceTest {

    private static final String EMAIL = "account@example.com";
    private static final UUID SESSION_ID = UUID.randomUUID();

    @Mock
    private UserRepository userRepository;

    @Mock
    private LoginSessionService sessionService;

    @Mock
    private SessionAccessGrantRepository grantRepository;

    @Mock
    private DeviceClaimService deviceClaimService;

    @Mock
    private DistributorAccessService distributorAccessService;

    @Mock
    private DeviceRepository deviceRepository;

    @Mock
    private DistributorBranchRepository branchRepository;

    @InjectMocks
    private SessionAccessVerificationService service;

    private User allowAccount(RoleName role, Long id) {
        User account = new User();
        ReflectionTestUtils.setField(account, "id", id);
        account.setRole(new Role(role));
        account.setStatus("ACTIVE");

        when(userRepository.findByEmailWithRole(EMAIL))
                .thenReturn(Optional.of(account));

        return account;
    }

    private ClaimDeviceRequest deviceRequest() {
        ClaimDeviceRequest request = new ClaimDeviceRequest();
        request.setDeviceCode("HALO1200-1");
        request.setSerialNumber("SN-001");
        return request;
    }

    private DistributorAccessRequest distributorRequest() {
        DistributorAccessRequest request = new DistributorAccessRequest();
        request.setDistributorCode("DIST-A");
        request.setBranchCode("MAIN");
        request.setLocation("EKM");
        return request;
    }

    private DeviceResponse deviceResponse() {
        return new DeviceResponse(
                1L, "HALO1200-1", "SN-001", "HALO 1200",
                null, 1L, 1L, 16L, "ACTIVE", null
        );
    }

    private DistributorAccessResponse distributorResponse() {
        return new DistributorAccessResponse(
                1L, "DIST-A", "DEV",
                1L, "MAIN", "ERNAKULAM", "EKM"
        );
    }

    private Device allowDeviceVerification(ClaimDeviceRequest request) {
        User account = allowAccount(RoleName.USER, 16L);

        Device device = new Device();
        ReflectionTestUtils.setField(device, "id", 1L);
        device.setOwner(account);

        when(deviceClaimService.claim(request, EMAIL))
                .thenReturn(deviceResponse());
        when(deviceRepository.findByIdAndOwner_Id(1L, 16L))
                .thenReturn(Optional.of(device));

        return device;
    }

    private DistributorBranch allowDistributorVerification(
            DistributorAccessRequest request
    ) {
        allowAccount(RoleName.DISTRIBUTOR_ADMIN, 22L);

        DistributorBranch branch = new DistributorBranch();
        ReflectionTestUtils.setField(branch, "id", 1L);

        when(distributorAccessService.verify(request, EMAIL))
                .thenReturn(distributorResponse());
        when(branchRepository.findByIdAndDistributor_Id(1L, 1L))
                .thenReturn(Optional.of(branch));

        return branch;
    }

    @Test
    void successfulDeviceVerificationSavesGrantForCurrentSession() {
        ClaimDeviceRequest request = deviceRequest();
        Device device = allowDeviceVerification(request);

        DeviceResponse response =
                service.verifyDevice(request, EMAIL, SESSION_ID);

        ArgumentCaptor<SessionAccessGrant> captured =
                ArgumentCaptor.forClass(SessionAccessGrant.class);

        verify(grantRepository).saveAndFlush(captured.capture());

        SessionAccessGrant grant = captured.getValue();
        assertEquals(SESSION_ID, grant.getSessionId());
        assertSame(device, grant.getDevice());
        assertNotNull(grant.getDeviceVerifiedAt());
        assertNull(grant.getBranch());
        assertNull(grant.getDistributorVerifiedAt());
        assertEquals(Long.valueOf(16), response.ownerUserId());

        var order = inOrder(sessionService, deviceClaimService, grantRepository);
        order.verify(sessionService).requireActiveForUpdate(SESSION_ID, 16L);
        order.verify(deviceClaimService).claim(request, EMAIL);
        order.verify(grantRepository).findById(SESSION_ID);
        order.verify(grantRepository).saveAndFlush(grant);
    }

    @Test
    void successfulDistributorVerificationSavesBranchGrant() {
        DistributorAccessRequest request = distributorRequest();
        DistributorBranch branch = allowDistributorVerification(request);

        DistributorAccessResponse response =
                service.verifyDistributor(request, EMAIL, SESSION_ID);

        ArgumentCaptor<SessionAccessGrant> captured =
                ArgumentCaptor.forClass(SessionAccessGrant.class);

        verify(grantRepository).saveAndFlush(captured.capture());

        SessionAccessGrant grant = captured.getValue();
        assertEquals(SESSION_ID, grant.getSessionId());
        assertSame(branch, grant.getBranch());
        assertNotNull(grant.getDistributorVerifiedAt());
        assertNull(grant.getDevice());
        assertNull(grant.getDeviceVerifiedAt());
        assertEquals("DIST-A", response.distributorCode());

        var order = inOrder(
                sessionService, distributorAccessService, grantRepository
        );
        order.verify(sessionService).requireActiveForUpdate(SESSION_ID, 22L);
        order.verify(distributorAccessService).verify(request, EMAIL);
        order.verify(grantRepository).findById(SESSION_ID);
        order.verify(grantRepository).saveAndFlush(grant);
    }

    @Test
    void repeatVerificationUpdatesExistingGrantAndClearsOtherRoleProof() {
        ClaimDeviceRequest request = deviceRequest();
        Device device = allowDeviceVerification(request);

        SessionAccessGrant existing = new SessionAccessGrant();
        existing.setSessionId(SESSION_ID);
        existing.setBranch(new DistributorBranch());
        existing.setDistributorVerifiedAt(LocalDateTime.now());

        when(grantRepository.findById(SESSION_ID))
                .thenReturn(Optional.of(existing));

        service.verifyDevice(request, EMAIL, SESSION_ID);

        verify(grantRepository).saveAndFlush(existing);
        assertSame(device, existing.getDevice());
        assertNotNull(existing.getDeviceVerifiedAt());
        assertNull(existing.getBranch());
        assertNull(existing.getDistributorVerifiedAt());
    }

    @Test
    void separateLoginSessionsReceiveSeparateGrantRecords() {
        ClaimDeviceRequest request = deviceRequest();
        allowDeviceVerification(request);
        UUID secondSession = UUID.randomUUID();

        service.verifyDevice(request, EMAIL, SESSION_ID);
        service.verifyDevice(request, EMAIL, secondSession);

        ArgumentCaptor<SessionAccessGrant> captured =
                ArgumentCaptor.forClass(SessionAccessGrant.class);

        verify(grantRepository, times(2))
                .saveAndFlush(captured.capture());

        assertEquals(SESSION_ID,
                captured.getAllValues().get(0).getSessionId());
        assertEquals(secondSession,
                captured.getAllValues().get(1).getSessionId());
        assertNotSame(
                captured.getAllValues().get(0),
                captured.getAllValues().get(1)
        );

        verify(sessionService).requireActiveForUpdate(SESSION_ID, 16L);
        verify(sessionService).requireActiveForUpdate(secondSession, 16L);
    }

    @Test
    void invalidSessionStopsVerificationBeforeClaimOrGrant() {
        allowAccount(RoleName.USER, 16L);

        when(sessionService.requireActiveForUpdate(SESSION_ID, 16L))
                .thenThrow(new InvalidTokenException("Invalid session"));

        assertThrows(InvalidTokenException.class,
                () -> service.verifyDevice(
                        deviceRequest(), EMAIL, SESSION_ID
                ));

        verifyNoInteractions(
                deviceClaimService, distributorAccessService,
                grantRepository, deviceRepository, branchRepository
        );
    }

    @Test
    void failedDeviceVerificationCannotSaveGrant() {
        allowAccount(RoleName.USER, 16L);
        ClaimDeviceRequest request = deviceRequest();

        when(deviceClaimService.claim(request, EMAIL))
                .thenThrow(new IllegalArgumentException(
                        "Device verification failed"
                ));

        assertThrows(IllegalArgumentException.class,
                () -> service.verifyDevice(request, EMAIL, SESSION_ID));

        verifyNoInteractions(grantRepository, deviceRepository);
    }

    @Test
    void failedDistributorVerificationCannotSaveGrant() {
        allowAccount(RoleName.DISTRIBUTOR_ADMIN, 22L);
        DistributorAccessRequest request = distributorRequest();

        when(distributorAccessService.verify(request, EMAIL))
                .thenThrow(new IllegalArgumentException(
                        "Distributor verification failed"
                ));

        assertThrows(IllegalArgumentException.class,
                () -> service.verifyDistributor(
                        request, EMAIL, SESSION_ID
                ));

        verifyNoInteractions(grantRepository, branchRepository);
    }

    @Test
    void deviceOwnershipLookupMustSucceedBeforeSavingGrant() {
        allowAccount(RoleName.USER, 16L);
        ClaimDeviceRequest request = deviceRequest();

        when(deviceClaimService.claim(request, EMAIL))
                .thenReturn(deviceResponse());

        assertThrows(IllegalArgumentException.class,
                () -> service.verifyDevice(request, EMAIL, SESSION_ID));

        verify(deviceRepository).findByIdAndOwner_Id(1L, 16L);
        verifyNoInteractions(grantRepository);
    }

    @Test
    void wrongRoleIsForbiddenBeforeSessionOrVerificationLookup() {
        allowAccount(RoleName.HYKON_ADMIN, 5L);

        ResponseStatusException error = assertThrows(
                ResponseStatusException.class,
                () -> service.verifyDevice(
                        deviceRequest(), EMAIL, SESSION_ID
                )
        );

        assertEquals(HttpStatus.FORBIDDEN, error.getStatusCode());

        assertThrows(ResponseStatusException.class,
                () -> service.verifyDistributor(
                        distributorRequest(), EMAIL, SESSION_ID
                ));

        verifyNoInteractions(
                sessionService, deviceClaimService,
                distributorAccessService, grantRepository,
                deviceRepository, branchRepository
        );
    }

    @Test
    void inactiveAccountCannotSaveVerification() {
        User account = allowAccount(RoleName.USER, 16L);
        account.setStatus("INACTIVE");

        assertThrows(ResponseStatusException.class,
                () -> service.verifyDevice(
                        deviceRequest(), EMAIL, SESSION_ID
                ));

        verifyNoInteractions(
                sessionService, deviceClaimService, grantRepository
        );
    }
}