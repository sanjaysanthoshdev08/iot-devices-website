package com.hykon.iot_backend.service;

import com.hykon.iot_backend.dto.DeviceResponse;
import com.hykon.iot_backend.dto.RegisterDeviceRequest;
import com.hykon.iot_backend.entity.*;
import com.hykon.iot_backend.exception.DuplicateResourceException;
import com.hykon.iot_backend.repository.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DeviceRegistrationServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private DeviceRepository deviceRepository;

    @Mock
    private DistributorBranchRepository branchRepository;

    private DeviceRegistrationService service;

    @BeforeEach
    void setUp() {
        service = new DeviceRegistrationService(
                userRepository, deviceRepository, branchRepository
        );
    }

    private User account(Long id, RoleName role, String status) {
        User user = new User();
        ReflectionTestUtils.setField(user, "id", id);
        user.setRole(new Role(role));
        user.setStatus(status);
        return user;
    }

    private void stubCreator(User creator) {
        when(userRepository.findByEmailWithRole("creator@example.com"))
                .thenReturn(Optional.of(creator));
    }

    private RegisterDeviceRequest request() {
        RegisterDeviceRequest request = new RegisterDeviceRequest();
        request.setDeviceCode(" halo1200-000001 ");
        request.setSerialNumber(" 2610000001 ");
        request.setSystemName(" HALO 1200 ");
        return request;
    }

    private DistributorBranch branch(
            User administrator,
            String branchStatus,
            String distributorStatus
    ) {
        Distributor distributor = new Distributor();
        ReflectionTestUtils.setField(distributor, "id", 1L);
        distributor.setAdministrator(administrator);
        distributor.setStatus(distributorStatus);

        DistributorBranch branch = new DistributorBranch();
        ReflectionTestUtils.setField(branch, "id", 1L);
        branch.setDistributor(distributor);
        branch.setStatus(branchStatus);
        return branch;
    }

    private void allowSave() {
        when(deviceRepository.saveAndFlush(any(Device.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void hykonAdminCanRegisterUnassignedInventory() {
        User creator = account(1L, RoleName.HYKON_ADMIN, "ACTIVE");
        stubCreator(creator);
        allowSave();

        DeviceResponse response =
                service.register(request(), "creator@example.com");

        assertEquals("halo1200-000001", response.deviceCode());
        assertEquals("2610000001", response.serialNumber());
        assertEquals("HALO 1200", response.systemName());
        assertEquals("ACTIVE", response.status());
        assertNull(response.ownerUserId());
        assertNull(response.branchId());

        ArgumentCaptor<Device> saved = ArgumentCaptor.forClass(Device.class);
        verify(deviceRepository).saveAndFlush(saved.capture());
        assertSame(creator, saved.getValue().getCreatedBy());
        assertNull(saved.getValue().getOwner());
        verifyNoInteractions(branchRepository);
    }

    @Test
    void distributorAdminCanRegisterOnlyWithinOwnBranch() {
        User creator = account(22L, RoleName.DISTRIBUTOR_ADMIN, "ACTIVE");
        stubCreator(creator);

        DistributorBranch ownBranch = branch(creator, "ACTIVE", "ACTIVE");
        when(branchRepository.findById(1L))
                .thenReturn(Optional.of(ownBranch));
        allowSave();

        RegisterDeviceRequest request = request();
        request.setBranchId(1L);

        DeviceResponse response =
                service.register(request, "creator@example.com");

        assertEquals(Long.valueOf(1), response.branchId());
        assertEquals(Long.valueOf(1), response.distributorId());
        assertNull(response.ownerUserId());

        ArgumentCaptor<Device> saved = ArgumentCaptor.forClass(Device.class);
        verify(deviceRepository).saveAndFlush(saved.capture());
        assertSame(ownBranch, saved.getValue().getBranch());
        assertSame(creator, saved.getValue().getCreatedBy());
    }

    @Test
    void customersAndInactiveAdminsCannotRegisterHardware() {
        for (User creator : new User[] {
                account(2L, RoleName.USER, "ACTIVE"),
                account(1L, RoleName.HYKON_ADMIN, "INACTIVE"),
                account(22L, RoleName.DISTRIBUTOR_ADMIN, "INACTIVE")
        }) {
            stubCreator(creator);

            assertThrows(AccessDeniedException.class,
                    () -> service.register(request(), "creator@example.com"));
        }

        verifyNoInteractions(deviceRepository, branchRepository);
    }

    @Test
    void distributorAdminCannotRegisterWithoutBranch() {
        stubCreator(account(22L, RoleName.DISTRIBUTOR_ADMIN, "ACTIVE"));

        assertThrows(AccessDeniedException.class,
                () -> service.register(request(), "creator@example.com"));

        verifyNoInteractions(deviceRepository, branchRepository);
    }

    @Test
    void foreignOrInactiveBranchesAreDeniedBeforeDeviceLookup() {
        User creator = account(22L, RoleName.DISTRIBUTOR_ADMIN, "ACTIVE");
        User otherAdmin = account(23L, RoleName.DISTRIBUTOR_ADMIN, "ACTIVE");
        stubCreator(creator);

        RegisterDeviceRequest request = request();
        request.setBranchId(1L);

        for (DistributorBranch unavailable : new DistributorBranch[] {
                branch(otherAdmin, "ACTIVE", "ACTIVE"),
                branch(creator, "INACTIVE", "ACTIVE"),
                branch(creator, "ACTIVE", "INACTIVE")
        }) {
            when(branchRepository.findById(1L))
                    .thenReturn(Optional.of(unavailable));

            assertThrows(AccessDeniedException.class,
                    () -> service.register(request, "creator@example.com"));
        }

        verifyNoInteractions(deviceRepository);
    }

    @Test
    void duplicateDeviceCodeIsRejected() {
        stubCreator(account(1L, RoleName.HYKON_ADMIN, "ACTIVE"));
        when(deviceRepository.findByCode("halo1200-000001"))
                .thenReturn(Optional.of(new Device()));

        assertThrows(DuplicateResourceException.class,
                () -> service.register(request(), "creator@example.com"));

        verify(deviceRepository, never()).saveAndFlush(any());
    }

    @Test
    void duplicateSerialNumberIsRejected() {
        stubCreator(account(1L, RoleName.HYKON_ADMIN, "ACTIVE"));
        when(deviceRepository.findBySerialNumberNormalized("2610000001"))
                .thenReturn(Optional.of(new Device()));

        assertThrows(DuplicateResourceException.class,
                () -> service.register(request(), "creator@example.com"));

        verify(deviceRepository, never()).saveAndFlush(any());
    }

    @Test
    void formatValidationRejectsInvalidSerialNumbers() {
        // Not 10 digits
        assertThrows(IllegalArgumentException.class,
                () -> DeviceRegistrationService.validateDeviceCredentialsFormat("halo1200-000001", "123"));
        assertThrows(IllegalArgumentException.class,
                () -> DeviceRegistrationService.validateDeviceCredentialsFormat("halo1200-000001", "26100001"));

        // Invalid month
        assertThrows(IllegalArgumentException.class,
                () -> DeviceRegistrationService.validateDeviceCredentialsFormat("halo1200-000001", "2600000001"));
        assertThrows(IllegalArgumentException.class,
                () -> DeviceRegistrationService.validateDeviceCredentialsFormat("halo1200-000001", "2613000001"));

        // Device number out of bounds
        assertThrows(IllegalArgumentException.class,
                () -> DeviceRegistrationService.validateDeviceCredentialsFormat("halo1200-000000", "2610000000"));
        assertThrows(IllegalArgumentException.class,
                () -> DeviceRegistrationService.validateDeviceCredentialsFormat("halo1200-100001", "2610100001"));
    }

    @Test
    void formatValidationRejectsInvalidDeviceCodes() {
        // Missing suffix
        assertThrows(IllegalArgumentException.class,
                () -> DeviceRegistrationService.validateDeviceCredentialsFormat("halo1200", "2610000001"));
        // Wrong suffix length
        assertThrows(IllegalArgumentException.class,
                () -> DeviceRegistrationService.validateDeviceCredentialsFormat("halo1200-1", "2610000001"));
    }

    @Test
    void formatValidationRejectsMismatchedSuffixes() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> DeviceRegistrationService.validateDeviceCredentialsFormat("halo1200-000002", "2610000001"));
        assertTrue(ex.getMessage().contains("must match"));
    }

    @Test
    void formatValidationAcceptsValidExamples() {
        // Serial 2610000001 and Device ID halo1200-000001
        assertDoesNotThrow(() -> DeviceRegistrationService.validateDeviceCredentialsFormat("halo1200-000001", "2610000001"));
        // Serial 2610000100 and Device ID halo1200-000100
        assertDoesNotThrow(() -> DeviceRegistrationService.validateDeviceCredentialsFormat("halo1200-000100", "2610000100"));
        // Serial 2610100000 and Device ID halo1200-100000 (boundary 100000)
        assertDoesNotThrow(() -> DeviceRegistrationService.validateDeviceCredentialsFormat("halo1200-100000", "2610100000"));
        // Model egen-000001
        assertDoesNotThrow(() -> DeviceRegistrationService.validateDeviceCredentialsFormat("egen-000001", "2605000001"));
    }

    @Test
    void formatValidationRejectsBoundaryViolationsAndLegacyFormats() {
        // Month 00 invalid
        IllegalArgumentException ex1 = assertThrows(IllegalArgumentException.class,
                () -> DeviceRegistrationService.validateDeviceCredentialsFormat("halo1200-000001", "2600000001"));
        assertTrue(ex1.getMessage().contains("month must be between 01 and 12"));

        // Month 13 invalid
        IllegalArgumentException ex2 = assertThrows(IllegalArgumentException.class,
                () -> DeviceRegistrationService.validateDeviceCredentialsFormat("halo1200-000001", "2613000001"));
        assertTrue(ex2.getMessage().contains("month must be between 01 and 12"));

        // Device number 000000 (below 000001) invalid
        IllegalArgumentException ex3 = assertThrows(IllegalArgumentException.class,
                () -> DeviceRegistrationService.validateDeviceCredentialsFormat("halo1200-000000", "2610000000"));
        assertTrue(ex3.getMessage().contains("between 000001 and 100000"));

        // Device number 100001 (above 100000) invalid
        IllegalArgumentException ex4 = assertThrows(IllegalArgumentException.class,
                () -> DeviceRegistrationService.validateDeviceCredentialsFormat("halo1200-100001", "2610100001"));
        assertTrue(ex4.getMessage().contains("between 000001 and 100000"));

        // Suffix mismatch (000002 vs 000001)
        IllegalArgumentException ex5 = assertThrows(IllegalArgumentException.class,
                () -> DeviceRegistrationService.validateDeviceCredentialsFormat("halo1200-000002", "2610000001"));
        assertTrue(ex5.getMessage().contains("must match"));

        // Legacy format without model prefix or YYMM rejected for new registrations
        assertThrows(IllegalArgumentException.class,
                () -> DeviceRegistrationService.validateDeviceCredentialsFormat("DEV-100", "SN-100"));
    }

    @Test
    void databaseConflictIsReportedWithoutSuccessfulRegistration() {
        stubCreator(account(1L, RoleName.HYKON_ADMIN, "ACTIVE"));
        when(deviceRepository.saveAndFlush(any(Device.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate"));

        assertThrows(DuplicateResourceException.class,
                () -> service.register(request(), "creator@example.com"));
    }
}