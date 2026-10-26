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
        request.setDeviceCode(" halo1200-1 ");
        request.setSerialNumber(" sn-001 ");
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

        assertEquals("HALO1200-1", response.deviceCode());
        assertEquals("SN-001", response.serialNumber());
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
        when(deviceRepository.findByCode("HALO1200-1"))
                .thenReturn(Optional.of(new Device()));

        assertThrows(DuplicateResourceException.class,
                () -> service.register(request(), "creator@example.com"));

        verify(deviceRepository, never()).saveAndFlush(any());
    }

    @Test
    void duplicateSerialNumberIsRejected() {
        stubCreator(account(1L, RoleName.HYKON_ADMIN, "ACTIVE"));
        when(deviceRepository.findBySerialNumberNormalized("SN-001"))
                .thenReturn(Optional.of(new Device()));

        assertThrows(DuplicateResourceException.class,
                () -> service.register(request(), "creator@example.com"));

        verify(deviceRepository, never()).saveAndFlush(any());
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