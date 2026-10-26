package com.hykon.iot_backend.service;

import com.hykon.iot_backend.dto.*;
import com.hykon.iot_backend.entity.*;
import com.hykon.iot_backend.exception.DuplicateResourceException;
import com.hykon.iot_backend.repository.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
class DevicePurchaseServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private DeviceRepository deviceRepository;

    @Mock
    private DevicePurchaseRepository purchaseRepository;

    private DevicePurchaseService service;

    @BeforeEach
    void setUp() {
        service = new DevicePurchaseService(
                userRepository, deviceRepository, purchaseRepository
        );
    }

    private User account(Long id, RoleName role, String status) {
        User user = new User();
        ReflectionTestUtils.setField(user, "id", id);
        user.setRole(new Role(role));
        user.setStatus(status);
        return user;
    }

    private void creator(User user) {
        when(userRepository.findByEmailWithRole("creator@example.com"))
                .thenReturn(Optional.of(user));
    }

    private Device device(Long distributorAdminId) {
        Distributor distributor = new Distributor();
        distributor.setAdministrator(account(
                distributorAdminId, RoleName.DISTRIBUTOR_ADMIN, "ACTIVE"
        ));
        distributor.setStatus("ACTIVE");

        DistributorBranch branch = new DistributorBranch();
        branch.setDistributor(distributor);
        branch.setStatus("ACTIVE");

        Device device = new Device();
        ReflectionTestUtils.setField(device, "id", 1L);
        device.setDeviceCode("HALO1200-1");
        device.setBranch(branch);
        device.setStatus("ACTIVE");
        return device;
    }

    private RecordDevicePurchaseRequest request() {
        RecordDevicePurchaseRequest request = new RecordDevicePurchaseRequest();
        request.setDeviceId(1L);
        request.setCustomerUserId(30L);
        request.setPurchaseReference(" INV-001 ");
        return request;
    }

    private Device allowHykonAndDevice() {
        creator(account(1L, RoleName.HYKON_ADMIN, "ACTIVE"));
        Device device = device(22L);
        when(deviceRepository.findByIdForUpdate(1L))
                .thenReturn(Optional.of(device));
        return device;
    }

    @Test
    void authorizedAdminsRecordEligibilityWithoutAssigningOwnership() {
        for (RoleName role : new RoleName[] {
                RoleName.HYKON_ADMIN, RoleName.DISTRIBUTOR_ADMIN
        }) {
            Long creatorId = role == RoleName.HYKON_ADMIN ? 1L : 22L;
            creator(account(creatorId, role, "ACTIVE"));

            Device device = device(22L);
            when(deviceRepository.findByIdForUpdate(1L))
                    .thenReturn(Optional.of(device));

            when(userRepository.findById(30L))
                    .thenReturn(Optional.of(
                            account(30L, RoleName.USER, "ACTIVE")
                    ));

            when(purchaseRepository.saveAndFlush(any(DevicePurchase.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            DevicePurchaseResponse response =
                    service.record(request(), "creator@example.com");

            assertEquals(Long.valueOf(1), response.deviceId());
            assertEquals(Long.valueOf(30), response.customerUserId());
            assertEquals(creatorId, response.recordedByUserId());
            assertEquals("INV-001", response.purchaseReference());
            assertEquals("ELIGIBLE", response.status());
            assertNull(response.claimedAt());
            assertNull(device.getOwner());
        }

        verify(purchaseRepository, times(2)).saveAndFlush(any());
    }

    @Test
    void customersAndInactiveAdminsCannotRecordPurchases() {
        for (User caller : new User[] {
                account(30L, RoleName.USER, "ACTIVE"),
                account(1L, RoleName.HYKON_ADMIN, "INACTIVE"),
                account(22L, RoleName.DISTRIBUTOR_ADMIN, "INACTIVE")
        }) {
            creator(caller);

            assertThrows(AccessDeniedException.class,
                    () -> service.record(request(), "creator@example.com"));
        }

        verifyNoInteractions(deviceRepository, purchaseRepository);
    }

    @Test
    void distributorCannotRecordPurchaseForAnotherDistributorDevice() {
        creator(account(22L, RoleName.DISTRIBUTOR_ADMIN, "ACTIVE"));
        when(deviceRepository.findByIdForUpdate(1L))
                .thenReturn(Optional.of(device(23L)));

        assertThrows(AccessDeniedException.class,
                () -> service.record(request(), "creator@example.com"));

        verifyNoInteractions(purchaseRepository);
        verify(userRepository, never()).findById(any());
    }

    @Test
    void existingOwnerCannotBeOverwrittenByPurchaseRecording() {
        Device device = allowHykonAndDevice();
        User existingOwner = account(31L, RoleName.USER, "ACTIVE");
        device.setOwner(existingOwner);

        assertThrows(DuplicateResourceException.class,
                () -> service.record(request(), "creator@example.com"));

        assertSame(existingOwner, device.getOwner());
        verifyNoInteractions(purchaseRepository);
    }

    @Test
    void existingPurchaseCannotBeReplaced() {
        allowHykonAndDevice();
        when(purchaseRepository.existsByDevice_Id(1L))
                .thenReturn(true);

        assertThrows(DuplicateResourceException.class,
                () -> service.record(request(), "creator@example.com"));

        verify(purchaseRepository, never()).saveAndFlush(any());
    }

    @Test
    void purchaserMustBeAnActiveUserCustomer() {
        allowHykonAndDevice();

        for (User customer : new User[] {
                account(30L, RoleName.USER, "INACTIVE"),
                account(30L, RoleName.HYKON_ADMIN, "ACTIVE"),
                account(30L, RoleName.DISTRIBUTOR_ADMIN, "ACTIVE")
        }) {
            when(userRepository.findById(30L))
                    .thenReturn(Optional.of(customer));

            assertThrows(IllegalArgumentException.class,
                    () -> service.record(request(), "creator@example.com"));
        }

        verify(purchaseRepository, never()).saveAndFlush(any());
    }

    @Test
    void databaseUniquenessConflictIsReported() {
        allowHykonAndDevice();
        when(userRepository.findById(30L))
                .thenReturn(Optional.of(
                        account(30L, RoleName.USER, "ACTIVE")
                ));

        when(purchaseRepository.saveAndFlush(any(DevicePurchase.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate"));

        assertThrows(DuplicateResourceException.class,
                () -> service.record(request(), "creator@example.com"));
    }
}