package com.hykon.iot_backend.service;

import com.hykon.iot_backend.dto.AuthorizeCustomerRequest;
import com.hykon.iot_backend.dto.DeviceResponse;
import com.hykon.iot_backend.entity.*;
import com.hykon.iot_backend.exception.DuplicateResourceException;
import com.hykon.iot_backend.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DeviceCustomerAccessServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private DeviceRepository deviceRepository;

    @Mock
    private DeviceCustomerAccessRepository customerAccessRepository;

    @Mock
    private SessionAccessGrantRepository grantRepository;

    private DeviceCustomerAccessService service;

    @BeforeEach
    void setUp() {
        service = new DeviceCustomerAccessService(
                userRepository, deviceRepository, customerAccessRepository, grantRepository
        );
    }

    private User account(Long id, String email, RoleName role, String status) {
        User user = new User();
        ReflectionTestUtils.setField(user, "id", id);
        user.setEmail(email);
        user.setRole(new Role(role));
        user.setStatus(status);
        return user;
    }

    private Device createDevice(Long deviceId, User owner) {
        Device device = new Device();
        ReflectionTestUtils.setField(device, "id", deviceId);
        device.setDeviceCode("halo1200-000001");
        device.setSerialNumber("2610000001");
        device.setSystemName("Halo 1200 Unit");
        device.setStatus("ACTIVE");
        device.setOwner(owner);
        return device;
    }

    @Test
    void adminCanAuthorizeSecondAndThirdCustomer() {
        User admin = account(1L, "admin@hykon.com", RoleName.HYKON_ADMIN, "ACTIVE");
        when(userRepository.findByEmailWithRole("admin@hykon.com")).thenReturn(Optional.of(admin));

        User owner = account(10L, "owner@customer.com", RoleName.USER, "ACTIVE");
        Device device = createDevice(100L, owner);
        when(deviceRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(device));

        User secondCustomer = account(20L, "second@customer.com", RoleName.USER, "ACTIVE");
        when(userRepository.findByEmailWithRole("second@customer.com")).thenReturn(Optional.of(secondCustomer));

        List<DeviceCustomerAccess> accessList = new ArrayList<>();
        accessList.add(new DeviceCustomerAccess(device, owner, true, admin));
        when(customerAccessRepository.findByDeviceIdForUpdate(100L)).thenReturn(accessList);

        when(customerAccessRepository.saveAndFlush(any(DeviceCustomerAccess.class)))
                .thenAnswer(inv -> {
                    DeviceCustomerAccess dca = inv.getArgument(0);
                    accessList.add(dca);
                    return dca;
                });

        when(customerAccessRepository.findByDeviceIdWithCustomer(100L))
                .thenAnswer(inv -> accessList);

        DeviceResponse response = service.authorizeCustomer(
                100L, new AuthorizeCustomerRequest("second@customer.com"), "admin@hykon.com"
        );

        assertNotNull(response);
        assertEquals(2, response.customerEmails().size());
        assertTrue(response.customerEmails().contains("owner@customer.com"));
        assertTrue(response.customerEmails().contains("second@customer.com"));
    }

    @Test
    void fourthCustomerAuthorizationIsRejected() {
        User admin = account(1L, "admin@hykon.com", RoleName.HYKON_ADMIN, "ACTIVE");
        when(userRepository.findByEmailWithRole("admin@hykon.com")).thenReturn(Optional.of(admin));

        User owner = account(10L, "owner@customer.com", RoleName.USER, "ACTIVE");
        Device device = createDevice(100L, owner);
        when(deviceRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(device));

        User cust2 = account(20L, "c2@test.com", RoleName.USER, "ACTIVE");
        User cust3 = account(30L, "c3@test.com", RoleName.USER, "ACTIVE");
        User cust4 = account(40L, "c4@test.com", RoleName.USER, "ACTIVE");

        when(userRepository.findByEmailWithRole("c4@test.com")).thenReturn(Optional.of(cust4));

        List<DeviceCustomerAccess> accessList = List.of(
                new DeviceCustomerAccess(device, owner, true, admin),
                new DeviceCustomerAccess(device, cust2, false, admin),
                new DeviceCustomerAccess(device, cust3, false, admin)
        );
        when(customerAccessRepository.findByDeviceIdForUpdate(100L)).thenReturn(accessList);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.authorizeCustomer(100L, new AuthorizeCustomerRequest("c4@test.com"), "admin@hykon.com"));
        assertTrue(ex.getMessage().contains("maximum of 3 authorized customers"));
    }

    @Test
    void duplicateCustomerAuthorizationIsIdempotent() {
        User admin = account(1L, "admin@hykon.com", RoleName.HYKON_ADMIN, "ACTIVE");
        when(userRepository.findByEmailWithRole("admin@hykon.com")).thenReturn(Optional.of(admin));

        User owner = account(10L, "owner@customer.com", RoleName.USER, "ACTIVE");
        Device device = createDevice(100L, owner);
        when(deviceRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(device));

        when(userRepository.findByEmailWithRole("owner@customer.com")).thenReturn(Optional.of(owner));

        List<DeviceCustomerAccess> accessList = List.of(
                new DeviceCustomerAccess(device, owner, true, admin)
        );
        when(customerAccessRepository.findByDeviceIdForUpdate(100L)).thenReturn(accessList);
        when(customerAccessRepository.findByDeviceIdWithCustomer(100L)).thenReturn(accessList);

        DeviceResponse response = service.authorizeCustomer(100L, new AuthorizeCustomerRequest("owner@customer.com"), "admin@hykon.com");
        assertNotNull(response);
        assertEquals(1, response.customerEmails().size());
        assertTrue(response.customerEmails().contains("owner@customer.com"));
        verify(customerAccessRepository, never()).saveAndFlush(any());
    }

    @Test
    void nonUserAccountCannotBeAuthorized() {
        User admin = account(1L, "admin@hykon.com", RoleName.HYKON_ADMIN, "ACTIVE");
        when(userRepository.findByEmailWithRole("admin@hykon.com")).thenReturn(Optional.of(admin));

        User owner = account(10L, "owner@customer.com", RoleName.USER, "ACTIVE");
        Device device = createDevice(100L, owner);
        when(deviceRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(device));

        User distAdmin = account(50L, "dist@test.com", RoleName.DISTRIBUTOR_ADMIN, "ACTIVE");
        when(userRepository.findByEmailWithRole("dist@test.com")).thenReturn(Optional.of(distAdmin));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.authorizeCustomer(100L, new AuthorizeCustomerRequest("dist@test.com"), "admin@hykon.com"));
        assertTrue(ex.getMessage().contains("must be an active user"));
    }

    @Test
    void primaryOwnerCannotBeRemoved() {
        User admin = account(1L, "admin@hykon.com", RoleName.HYKON_ADMIN, "ACTIVE");
        when(userRepository.findByEmailWithRole("admin@hykon.com")).thenReturn(Optional.of(admin));

        User owner = account(10L, "owner@customer.com", RoleName.USER, "ACTIVE");
        Device device = createDevice(100L, owner);
        when(deviceRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(device));

        DeviceCustomerAccess ownerAccess = new DeviceCustomerAccess(device, owner, true, admin);
        when(customerAccessRepository.findByDeviceIdAndCustomerId(100L, 10L)).thenReturn(Optional.of(ownerAccess));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.removeCustomerAccess(100L, 10L, "admin@hykon.com"));
        assertTrue(ex.getMessage().contains("Cannot remove the primary device owner"));
        verify(customerAccessRepository, never()).deleteByDeviceIdAndCustomerId(any(), any());
    }

    @Test
    void additionalCustomerCanBeRemovedAndGrantsInvalidated() {
        User admin = account(1L, "admin@hykon.com", RoleName.HYKON_ADMIN, "ACTIVE");
        when(userRepository.findByEmailWithRole("admin@hykon.com")).thenReturn(Optional.of(admin));

        User owner = account(10L, "owner@customer.com", RoleName.USER, "ACTIVE");
        Device device = createDevice(100L, owner);
        when(deviceRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(device));

        User cust2 = account(20L, "second@customer.com", RoleName.USER, "ACTIVE");
        DeviceCustomerAccess cust2Access = new DeviceCustomerAccess(device, cust2, false, admin);
        when(customerAccessRepository.findByDeviceIdAndCustomerId(100L, 20L)).thenReturn(Optional.of(cust2Access));

        SessionAccessGrant grant = new SessionAccessGrant();
        grant.setDevice(device);
        when(grantRepository.findAll()).thenReturn(List.of(grant));

        service.removeCustomerAccess(100L, 20L, "admin@hykon.com");

        verify(customerAccessRepository).deleteByDeviceIdAndCustomerId(100L, 20L);
        assertNull(grant.getDevice());
        verify(grantRepository).save(grant);
    }

    @Test
    void distributorAdminCannotManageAnotherDistributorsDevice() {
        User distAdmin = account(2L, "dist@test.com", RoleName.DISTRIBUTOR_ADMIN, "ACTIVE");
        when(userRepository.findByEmailWithRole("dist@test.com")).thenReturn(Optional.of(distAdmin));

        User otherAdmin = account(99L, "other@test.com", RoleName.DISTRIBUTOR_ADMIN, "ACTIVE");
        Distributor otherDistributor = new Distributor();
        otherDistributor.setAdministrator(otherAdmin);

        DistributorBranch branch = new DistributorBranch();
        branch.setDistributor(otherDistributor);

        Device device = createDevice(100L, null);
        device.setBranch(branch);
        when(deviceRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(device));

        assertThrows(AccessDeniedException.class,
                () -> service.authorizeCustomer(100L, new AuthorizeCustomerRequest("user@test.com"), "dist@test.com"));
    }

    @Test
    void nonAdminCannotAuthorizeCustomer() {
        User customer = account(10L, "user@test.com", RoleName.USER, "ACTIVE");
        when(userRepository.findByEmailWithRole("user@test.com")).thenReturn(Optional.of(customer));

        assertThrows(AccessDeniedException.class,
                () -> service.authorizeCustomer(100L, new AuthorizeCustomerRequest("other@test.com"), "user@test.com"));
    }

    @Test
    void removingNonExistentCustomerThrowsNotFound() {
        User admin = account(1L, "admin@hykon.com", RoleName.HYKON_ADMIN, "ACTIVE");
        when(userRepository.findByEmailWithRole("admin@hykon.com")).thenReturn(Optional.of(admin));

        Device device = createDevice(100L, null);
        when(deviceRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(device));
        when(customerAccessRepository.findByDeviceIdAndCustomerId(100L, 999L)).thenReturn(Optional.empty());

        assertThrows(ResponseStatusException.class,
                () -> service.removeCustomerAccess(100L, 999L, "admin@hykon.com"));
    }
}

