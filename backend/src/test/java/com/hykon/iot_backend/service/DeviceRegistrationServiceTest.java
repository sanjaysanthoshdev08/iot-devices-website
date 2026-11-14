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

    @Mock
    private com.hykon.iot_backend.repository.DistributorRepository distributorRepository;

    private DeviceRegistrationService service;

    @BeforeEach
    void setUp() {
        service = new DeviceRegistrationService(
                userRepository, deviceRepository, branchRepository, distributorRepository
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
        request.setSerialNumber(" 261100001 ");
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

    /** Request carrying registered codes that resolve to a real branch. */
    private RegisterDeviceRequest requestWithCodes(User admin) {
        DistributorBranch branch = branch(admin, "ACTIVE", "ACTIVE");
        when(distributorRepository.findByCode("DIST-1"))
                .thenReturn(Optional.of(branch.getDistributor()));
        when(branchRepository.findByDistributorAndCode(1L, "BR-1"))
                .thenReturn(Optional.of(branch));
        when(branchRepository.findById(1L)).thenReturn(Optional.of(branch));
        RegisterDeviceRequest request = request();
        request.setDistributorCode("DIST-1");
        request.setBranchCode("BR-1");
        return request;
    }

    @Test
    void hykonAdminRegistersDeviceUnderVerifiedCodes() {
        User creator = account(1L, RoleName.HYKON_ADMIN, "ACTIVE");
        stubCreator(creator);
        allowSave();

        DeviceResponse response =
                service.register(requestWithCodes(creator), "creator@example.com");

        assertEquals("halo1200-000001", response.deviceCode());
        assertEquals("261100001", response.serialNumber());
        assertEquals("ACTIVE", response.status());
        assertNull(response.ownerUserId());
        assertEquals(Long.valueOf(1), response.branchId());
        assertEquals(Long.valueOf(1), response.distributorId());
    }

    @Test
    void registersWhenSelectedBranchBelongsToSelectedDistributor() {
        User creator = account(1L, RoleName.HYKON_ADMIN, "ACTIVE");
        stubCreator(creator);
        allowSave();
        DistributorBranch branch = branch(creator, "ACTIVE", "ACTIVE");
        when(distributorRepository.findById(1L)).thenReturn(Optional.of(branch.getDistributor()));
        when(branchRepository.findById(1L)).thenReturn(Optional.of(branch));
        RegisterDeviceRequest request = request();
        request.setDistributorId(1L);
        request.setBranchId(1L);

        DeviceResponse response = service.register(request, "creator@example.com");
        assertEquals(Long.valueOf(1), response.branchId());
    }

    @Test
    void selectedBranchUnderAnotherDistributorIsRejected() {
        User creator = account(1L, RoleName.HYKON_ADMIN, "ACTIVE");
        stubCreator(creator);
        DistributorBranch branch = branch(creator, "ACTIVE", "ACTIVE");
        Distributor other = new Distributor();
        ReflectionTestUtils.setField(other, "id", 2L);
        when(distributorRepository.findById(2L)).thenReturn(Optional.of(other));
        when(branchRepository.findById(1L)).thenReturn(Optional.of(branch));
        RegisterDeviceRequest request = request();
        request.setDistributorId(2L);
        request.setBranchId(1L);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.register(request, "creator@example.com"));
        assertTrue(ex.getMessage().contains("does not belong"));
        verify(deviceRepository, never()).saveAndFlush(any());
    }

    @Test
    void unknownDistributorCodeIsRejectedAndNothingSaved() {
        stubCreator(account(1L, RoleName.HYKON_ADMIN, "ACTIVE"));
        when(distributorRepository.findByCode("NOPE")).thenReturn(Optional.empty());
        RegisterDeviceRequest request = request();
        request.setDistributorCode("NOPE");
        request.setBranchCode("BR-1");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.register(request, "creator@example.com"));
        assertTrue(ex.getMessage().contains("was not found"));
        verify(deviceRepository, never()).saveAndFlush(any());
    }

    @Test
    void branchNotUnderDistributorIsRejectedAndNothingSaved() {
        User creator = account(1L, RoleName.HYKON_ADMIN, "ACTIVE");
        stubCreator(creator);
        Distributor distributor = new Distributor();
        ReflectionTestUtils.setField(distributor, "id", 1L);
        when(distributorRepository.findByCode("DIST-1")).thenReturn(Optional.of(distributor));
        when(branchRepository.findByDistributorAndCode(1L, "BR-X")).thenReturn(Optional.empty());
        RegisterDeviceRequest request = request();
        request.setDistributorCode("DIST-1");
        request.setBranchCode("BR-X");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.register(request, "creator@example.com"));
        assertTrue(ex.getMessage().contains("does not exist under distributor"));
        verify(deviceRepository, never()).saveAndFlush(any());
    }

    @Test
    void hykonAdminMustProvideCodes() {
        stubCreator(account(1L, RoleName.HYKON_ADMIN, "ACTIVE"));

        assertThrows(IllegalArgumentException.class,
                () -> service.register(request(), "creator@example.com"));
        verify(deviceRepository, never()).saveAndFlush(any());
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

        assertThrows(IllegalArgumentException.class,
                () -> service.register(request(), "creator@example.com"));

        verifyNoInteractions(deviceRepository, branchRepository);
    }

    @Test
    void duplicateDeviceCodeIsRejected() {
        User admin = account(1L, RoleName.HYKON_ADMIN, "ACTIVE");
        stubCreator(admin);
        RegisterDeviceRequest request = requestWithCodes(admin);
        when(deviceRepository.findByCode("halo1200-000001"))
                .thenReturn(Optional.of(new Device()));

        DuplicateResourceException ex = assertThrows(DuplicateResourceException.class,
                () -> service.register(request, "creator@example.com"));
        assertEquals("This system is already registered", ex.getMessage());

        verify(deviceRepository, never()).saveAndFlush(any());
    }

    @Test
    void duplicateSerialNumberIsRejected() {
        User admin = account(1L, RoleName.HYKON_ADMIN, "ACTIVE");
        stubCreator(admin);
        RegisterDeviceRequest request = requestWithCodes(admin);
        when(deviceRepository.findBySerialNumberNormalized("261100001"))
                .thenReturn(Optional.of(new Device()));

        assertThrows(DuplicateResourceException.class,
                () -> service.register(request, "creator@example.com"));

        verify(deviceRepository, never()).saveAndFlush(any());
    }

    @Test
    void formatValidationRejectsInvalidSerialNumbers() {
        // Not 9 digits (old 10-digit format, too short, non-numeric)
        assertThrows(IllegalArgumentException.class,
                () -> DeviceRegistrationService.validateDeviceCredentialsFormat("halo1200-1", "123"));
        assertThrows(IllegalArgumentException.class,
                () -> DeviceRegistrationService.validateDeviceCredentialsFormat("halo1200-1", "26110001"));
        assertThrows(IllegalArgumentException.class,
                () -> DeviceRegistrationService.validateDeviceCredentialsFormat("halo1200-1", "2610000001"));
        assertThrows(IllegalArgumentException.class,
                () -> DeviceRegistrationService.validateDeviceCredentialsFormat("halo1200-1", "SN-HYK-1200-01"));

        // Invalid month
        assertThrows(IllegalArgumentException.class,
                () -> DeviceRegistrationService.validateDeviceCredentialsFormat("halo1200-1", "260000001"));
        assertThrows(IllegalArgumentException.class,
                () -> DeviceRegistrationService.validateDeviceCredentialsFormat("halo1200-1", "261300001"));

        // Device number 00000 is not allowed
        assertThrows(IllegalArgumentException.class,
                () -> DeviceRegistrationService.validateDeviceCredentialsFormat("halo1200-1", "261100000"));
    }

    @Test
    void formatValidationRejectsInvalidDeviceCodes() {
        // Missing number
        assertThrows(IllegalArgumentException.class,
                () -> DeviceRegistrationService.validateDeviceCredentialsFormat("halo1200", "261100001"));
        assertThrows(IllegalArgumentException.class,
                () -> DeviceRegistrationService.validateDeviceCredentialsFormat("halo1200-", "261100001"));
        // Missing model
        assertThrows(IllegalArgumentException.class,
                () -> DeviceRegistrationService.validateDeviceCredentialsFormat("-1", "261100001"));
        // Non-numeric number
        assertThrows(IllegalArgumentException.class,
                () -> DeviceRegistrationService.validateDeviceCredentialsFormat("halo1200-abc", "261100001"));
    }

    @Test
    void formatValidationAcceptsValidExamples() {
        // Device 1, 2, 3 of November 2026
        assertDoesNotThrow(() -> DeviceRegistrationService.validateDeviceCredentialsFormat("HALO1200-1", "261100001"));
        assertDoesNotThrow(() -> DeviceRegistrationService.validateDeviceCredentialsFormat("HALO1200-2", "261100002"));
        assertDoesNotThrow(() -> DeviceRegistrationService.validateDeviceCredentialsFormat("HALO1200-3", "261100003"));
        // Numbering restarts in December; Device ID does not have to match the serial
        assertDoesNotThrow(() -> DeviceRegistrationService.validateDeviceCredentialsFormat("halo1200-000004", "261200001"));
        // Upper bound of the running number
        assertDoesNotThrow(() -> DeviceRegistrationService.validateDeviceCredentialsFormat("egen-99999", "260599999"));
    }

    @Test
    void formatValidationReportsClearMessages() {
        IllegalArgumentException length = assertThrows(IllegalArgumentException.class,
                () -> DeviceRegistrationService.validateDeviceCredentialsFormat("halo1200-1", "2610000001"));
        assertTrue(length.getMessage().contains("9 digits"));

        IllegalArgumentException month = assertThrows(IllegalArgumentException.class,
                () -> DeviceRegistrationService.validateDeviceCredentialsFormat("halo1200-1", "261300001"));
        assertTrue(month.getMessage().contains("month must be between 01 and 12"));

        IllegalArgumentException number = assertThrows(IllegalArgumentException.class,
                () -> DeviceRegistrationService.validateDeviceCredentialsFormat("halo1200-1", "261100000"));
        assertTrue(number.getMessage().contains("between 00001 and 99999"));

        // Legacy format without model prefix or YYMM rejected for new registrations
        assertThrows(IllegalArgumentException.class,
                () -> DeviceRegistrationService.validateDeviceCredentialsFormat("DEV-100", "SN-100"));
    }

    @Test
    void databaseConflictIsReportedWithoutSuccessfulRegistration() {
        User admin = account(1L, RoleName.HYKON_ADMIN, "ACTIVE");
        stubCreator(admin);
        RegisterDeviceRequest request = requestWithCodes(admin);
        when(deviceRepository.saveAndFlush(any(Device.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate"));

        assertThrows(DuplicateResourceException.class,
                () -> service.register(request, "creator@example.com"));
    }

    @Test
    void inactiveBranchesAreDeniedBeforeDeviceLookup() {
        User creator = account(22L, RoleName.DISTRIBUTOR_ADMIN, "ACTIVE");
        stubCreator(creator);

        RegisterDeviceRequest request = request();
        request.setBranchId(1L);

        for (DistributorBranch unavailable : new DistributorBranch[] {
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
    void scopedDistributorAdminCannotRegisterInAnotherDistributorsBranch() {
        User creator = account(22L, RoleName.DISTRIBUTOR_ADMIN, "ACTIVE");
        User someoneElse = account(23L, RoleName.DISTRIBUTOR_ADMIN, "ACTIVE");
        stubCreator(creator);
        DistributorBranch other = branch(someoneElse, "ACTIVE", "ACTIVE");
        when(branchRepository.findById(1L)).thenReturn(Optional.of(other));

        RegisterDeviceRequest request = request();
        request.setBranchId(1L);

        assertThrows(AccessDeniedException.class,
                () -> service.register(request, "creator@example.com"));
        verify(deviceRepository, never()).saveAndFlush(any());
    }

    @Test
    void distributorAdminCanRegisterInAnyDistributorsBranch() {
        User creator = account(22L, RoleName.DISTRIBUTOR_ADMIN, "ACTIVE");
        creator.setAllDistributors(true);
        User someoneElse = account(23L, RoleName.DISTRIBUTOR_ADMIN, "ACTIVE");
        stubCreator(creator);
        allowSave();
        DistributorBranch other = branch(someoneElse, "ACTIVE", "ACTIVE");
        when(branchRepository.findById(1L)).thenReturn(Optional.of(other));

        RegisterDeviceRequest request = request();
        request.setBranchId(1L);

        assertEquals(Long.valueOf(1),
                service.register(request, "creator@example.com").branchId());
    }
}
