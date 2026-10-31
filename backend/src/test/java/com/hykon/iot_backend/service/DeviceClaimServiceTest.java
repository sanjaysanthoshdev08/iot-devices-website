package com.hykon.iot_backend.service;

import com.hykon.iot_backend.dto.ClaimDeviceRequest;
import com.hykon.iot_backend.dto.DeviceResponse;
import com.hykon.iot_backend.entity.*;
import com.hykon.iot_backend.repository.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DeviceClaimServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private DeviceRepository deviceRepository;

    @Mock
    private DevicePurchaseRepository purchaseRepository;

    @Mock
    private DeviceCustomerAccessRepository customerAccessRepository;

    private DeviceClaimService service;

    @BeforeEach
    void setUp() {
        service = new DeviceClaimService(
                userRepository, deviceRepository, purchaseRepository, customerAccessRepository
        );
    }

    private User account(Long id, RoleName role, String status) {
        User user = new User();
        ReflectionTestUtils.setField(user, "id", id);
        user.setRole(new Role(role));
        user.setStatus(status);
        return user;
    }

    private ClaimDeviceRequest request() {
        ClaimDeviceRequest request = new ClaimDeviceRequest();
        request.setDeviceCode("HALO1200-1");
        request.setSerialNumber(" sn-001 ");
        return request;
    }

    private void allowCustomer() {
        when(userRepository.findByEmailWithRole("customer@example.com"))
                .thenReturn(Optional.of(
                        account(30L, RoleName.USER, "ACTIVE")
                ));
    }

    private Device prepareDevice() {
        allowCustomer();

        Distributor distributor = new Distributor();
        ReflectionTestUtils.setField(distributor, "id", 1L);
        distributor.setStatus("ACTIVE");

        DistributorBranch branch = new DistributorBranch();
        ReflectionTestUtils.setField(branch, "id", 1L);
        branch.setDistributor(distributor);
        branch.setStatus("ACTIVE");

        Device device = new Device();
        ReflectionTestUtils.setField(device, "id", 1L);
        device.setDeviceCode("HALO1200-1");
        device.setSerialNumber("SN-001");
        device.setSystemName("HALO 1200");
        device.setBranch(branch);
        device.setStatus("ACTIVE");

        when(deviceRepository.findByCodeForUpdate("HALO1200-1"))
                .thenReturn(Optional.of(device));

        return device;
    }

    private DevicePurchase preparePurchase(
            Device device, Long customerId, String status
    ) {
        DevicePurchase purchase = new DevicePurchase();
        purchase.setDevice(device);
        purchase.setCustomer(account(customerId, RoleName.USER, "ACTIVE"));
        purchase.setStatus(status);

        when(purchaseRepository.findByDeviceIdForUpdate(1L))
                .thenReturn(Optional.of(purchase));

        return purchase;
    }

    private void assertVerificationFailure(ClaimDeviceRequest request) {
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> service.claim(request, "customer@example.com")
        );

        assertEquals("Device verification failed", error.getMessage());
        verify(deviceRepository, never()).saveAndFlush(any());
        verify(purchaseRepository, never()).saveAndFlush(any());
    }

    @Test
    void eligiblePurchaserClaimsDeviceAndMarksPurchaseClaimed() {
        Device device = prepareDevice();
        DevicePurchase purchase = preparePurchase(device, 30L, "ELIGIBLE");

        when(deviceRepository.saveAndFlush(any(Device.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(purchaseRepository.saveAndFlush(any(DevicePurchase.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        DeviceResponse response =
                service.claim(request(), "customer@example.com");

        assertEquals(Long.valueOf(30), response.ownerUserId());
        assertEquals(Long.valueOf(30), device.getOwner().getId());
        assertEquals("CLAIMED", purchase.getStatus());
        assertNotNull(purchase.getClaimedAt());

        InOrder order = inOrder(deviceRepository, purchaseRepository);
        order.verify(deviceRepository).findByCodeForUpdate("HALO1200-1");
        order.verify(purchaseRepository).findByDeviceIdForUpdate(1L);
        order.verify(deviceRepository).saveAndFlush(device);
        order.verify(purchaseRepository).saveAndFlush(purchase);
    }

    @Test
    void recordedOwnerCanVerifyAgainWithoutChangingOwnership() {
        Device device = prepareDevice();
        device.setOwner(account(30L, RoleName.USER, "ACTIVE"));

        DevicePurchase purchase = preparePurchase(device, 30L, "CLAIMED");
        LocalDateTime originalClaimTime = LocalDateTime.now().minusDays(1);
        purchase.setClaimedAt(originalClaimTime);

        DeviceResponse response =
                service.claim(request(), "customer@example.com");

        assertEquals(Long.valueOf(30), response.ownerUserId());
        assertEquals(originalClaimTime, purchase.getClaimedAt());
        verify(deviceRepository, never()).saveAndFlush(any());
        verify(purchaseRepository, never()).saveAndFlush(any());
    }

    @Test
    void anotherPurchasersDeviceCannotBeClaimed() {
        Device device = prepareDevice();
        preparePurchase(device, 31L, "ELIGIBLE");

        assertVerificationFailure(request());
        assertNull(device.getOwner());
    }

    @Test
    void wrongSerialNumberIsRejectedBeforePurchaseLookup() {
        prepareDevice();
        ClaimDeviceRequest request = request();
        request.setSerialNumber("WRONG-SERIAL");

        assertVerificationFailure(request);
        verifyNoInteractions(purchaseRepository);
    }

    @Test
    void deviceWithoutPurchaseEligibilityCannotBeClaimed() {
        prepareDevice();
        assertVerificationFailure(request());
    }

    @Test
    void revokedPurchaseCannotBeClaimed() {
        Device device = prepareDevice();
        preparePurchase(device, 30L, "REVOKED");

        assertVerificationFailure(request());
        assertNull(device.getOwner());
    }

    @Test
    void anotherOwnersDeviceCannotBeTransferredByClaim() {
        Device device = prepareDevice();
        User existingOwner = account(31L, RoleName.USER, "ACTIVE");
        device.setOwner(existingOwner);

        assertVerificationFailure(request());
        assertSame(existingOwner, device.getOwner());
        verifyNoInteractions(purchaseRepository);
    }

    @Test
    void adminsAndInactiveCustomersCannotClaim() {
        for (User caller : new User[] {
                account(1L, RoleName.HYKON_ADMIN, "ACTIVE"),
                account(22L, RoleName.DISTRIBUTOR_ADMIN, "ACTIVE"),
                account(30L, RoleName.USER, "INACTIVE")
        }) {
            when(userRepository.findByEmailWithRole("customer@example.com"))
                    .thenReturn(Optional.of(caller));

            assertThrows(AccessDeniedException.class,
                    () -> service.claim(request(), "customer@example.com"));
        }

        verifyNoInteractions(deviceRepository, purchaseRepository);
    }

    @Test
    void inactiveDeviceBranchOrDistributorCannotBeClaimed() {
        for (int inactivePart = 0; inactivePart < 3; inactivePart++) {
            Device device = prepareDevice();

            if (inactivePart == 0) {
                device.setStatus("INACTIVE");
            }
            else if (inactivePart == 1) {
                device.getBranch().setStatus("INACTIVE");
            }
            else {
                device.getBranch().getDistributor().setStatus("INACTIVE");
            }

            assertVerificationFailure(request());
        }

        verifyNoInteractions(purchaseRepository);
    }

    @Test
    void unknownDeviceReturnsGenericVerificationFailure() {
        allowCustomer();
        assertVerificationFailure(request());
        verifyNoInteractions(purchaseRepository);
    }

    @Test
    void authorizedSecondaryCustomerCanVerifyWithoutPurchaseRecord() {
        allowCustomer();
        Device device = prepareDevice();
        User existingOwner = account(10L, RoleName.USER, "ACTIVE");
        device.setOwner(existingOwner);

        when(customerAccessRepository.existsByDeviceIdAndCustomerId(device.getId(), 30L))
                .thenReturn(true);

        DeviceResponse response = service.claim(request(), "customer@example.com");
        assertNotNull(response);
        assertEquals(device.getDeviceCode(), response.deviceCode());
        verifyNoInteractions(purchaseRepository);
    }
}