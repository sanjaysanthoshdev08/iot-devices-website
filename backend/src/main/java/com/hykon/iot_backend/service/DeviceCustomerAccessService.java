package com.hykon.iot_backend.service;

import com.hykon.iot_backend.dto.AuthorizeCustomerRequest;
import com.hykon.iot_backend.dto.DeviceResponse;
import com.hykon.iot_backend.entity.*;
import com.hykon.iot_backend.exception.DuplicateResourceException;
import com.hykon.iot_backend.repository.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
@Transactional
public class DeviceCustomerAccessService {

    private final UserRepository userRepository;
    private final DeviceRepository deviceRepository;
    private final DeviceCustomerAccessRepository customerAccessRepository;
    private final SessionAccessGrantRepository grantRepository;

    public DeviceCustomerAccessService(
            UserRepository userRepository,
            DeviceRepository deviceRepository,
            DeviceCustomerAccessRepository customerAccessRepository,
            SessionAccessGrantRepository grantRepository
    ) {
        this.userRepository = userRepository;
        this.deviceRepository = deviceRepository;
        this.customerAccessRepository = customerAccessRepository;
        this.grantRepository = grantRepository;
    }

    public DeviceResponse authorizeCustomer(
            Long deviceId,
            AuthorizeCustomerRequest request,
            String adminEmail
    ) {
        User admin = requireAdmin(adminEmail);
        Device device = requireDeviceForAdmin(deviceId, admin);

        if (request == null || request.getCustomerEmail() == null || request.getCustomerEmail().isBlank()) {
            throw new IllegalArgumentException("Customer email is required");
        }

        String targetEmail = request.getCustomerEmail().trim().toLowerCase();
        User targetCustomer = userRepository.findByEmailWithRole(targetEmail)
                .orElseThrow(() -> new IllegalArgumentException("Target customer account not found"));

        if (!"ACTIVE".equalsIgnoreCase(targetCustomer.getStatus())
                || targetCustomer.getRole() == null
                || targetCustomer.getRole().getName() != RoleName.USER) {
            throw new IllegalArgumentException("Target customer must be an active user account");
        }

        List<DeviceCustomerAccess> currentAccess = customerAccessRepository.findByDeviceIdForUpdate(deviceId);

        // Ensure primary owner is counted if not already in access table
        if (device.getOwner() != null && currentAccess.stream().noneMatch(a -> a.getCustomer().getId().equals(device.getOwner().getId()))) {
            DeviceCustomerAccess ownerAccess = new DeviceCustomerAccess(device, device.getOwner(), true, admin);
            customerAccessRepository.saveAndFlush(ownerAccess);
            currentAccess = customerAccessRepository.findByDeviceIdForUpdate(deviceId);
        }

        if (currentAccess.stream().anyMatch(a -> a.getCustomer().getId().equals(targetCustomer.getId()))) {
            List<String> emails = customerAccessRepository.findByDeviceIdWithCustomer(deviceId)
                    .stream().map(a -> a.getCustomer().getEmail()).toList();
            return DeviceResponse.from(device, emails);
        }

        if (currentAccess.size() >= 3) {
            throw new IllegalArgumentException("Device already has the maximum of 3 authorized customers");
        }

        DeviceCustomerAccess newAccess = new DeviceCustomerAccess(device, targetCustomer, false, admin);
        customerAccessRepository.saveAndFlush(newAccess);

        List<String> emails = customerAccessRepository.findByDeviceIdWithCustomer(deviceId)
                .stream().map(a -> a.getCustomer().getEmail()).toList();

        return DeviceResponse.from(device, emails);
    }

    public void removeCustomerAccess(
            Long deviceId,
            Long customerUserId,
            String adminEmail
    ) {
        User admin = requireAdmin(adminEmail);
        Device device = requireDeviceForAdmin(deviceId, admin);

        if (customerUserId == null || customerUserId <= 0) {
            throw new IllegalArgumentException("Valid customer user ID is required");
        }

        DeviceCustomerAccess access = customerAccessRepository
                .findByDeviceIdAndCustomerId(deviceId, customerUserId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Customer is not authorized for this device"));

        if (access.isPrimaryOwner() || (device.getOwner() != null && device.getOwner().getId().equals(customerUserId))) {
            throw new IllegalArgumentException("Cannot remove the primary device owner");
        }

        customerAccessRepository.deleteByDeviceIdAndCustomerId(deviceId, customerUserId);

        // Invalidate active session grants for this customer on this device
        grantRepository.findAll().stream()
                .filter(g -> g.getDevice() != null && g.getDevice().getId().equals(deviceId))
                .forEach(g -> {
                    g.setDevice(null);
                    g.setDeviceVerifiedAt(null);
                    grantRepository.save(g);
                });
    }

    public List<String> getCustomerEmailsForDevice(Long deviceId) {
        List<DeviceCustomerAccess> accesses = customerAccessRepository.findByDeviceIdWithCustomer(deviceId);
        if (!accesses.isEmpty()) {
            return accesses.stream().map(a -> a.getCustomer().getEmail()).toList();
        }
        return deviceRepository.findById(deviceId)
                .map(d -> d.getOwner() != null ? List.of(d.getOwner().getEmail()) : List.<String>of())
                .orElse(List.of());
    }

    private User requireAdmin(String email) {
        if (email == null || email.isBlank()) {
            throw new AccessDeniedException("Administrator access is required");
        }
        User user = userRepository.findByEmailWithRole(email)
                .orElseThrow(() -> new AccessDeniedException("Administrator access is required"));
        if (!"ACTIVE".equalsIgnoreCase(user.getStatus()) || user.getRole() == null || user.getRole().getName() == null) {
            throw new AccessDeniedException("Administrator access is required");
        }
        RoleName role = user.getRole().getName();
        if (role != RoleName.HYKON_ADMIN && role != RoleName.DISTRIBUTOR_ADMIN) {
            throw new AccessDeniedException("Administrator access is required");
        }
        return user;
    }

    private Device requireDeviceForAdmin(Long deviceId, User admin) {
        Device device = deviceRepository.findByIdForUpdate(deviceId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Device not found"));

        if (admin.getRole().getName() == RoleName.DISTRIBUTOR_ADMIN) {
            DistributorBranch branch = device.getBranch();
            Distributor distributor = branch == null ? null : branch.getDistributor();
            if (distributor == null || distributor.getAdministrator() == null
                    || !admin.getId().equals(distributor.getAdministrator().getId())) {
                throw new AccessDeniedException("You do not have permission to manage this device");
            }
        }
        return device;
    }
}

