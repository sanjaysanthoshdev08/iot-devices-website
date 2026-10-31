package com.hykon.iot_backend.service;

import com.hykon.iot_backend.dto.ClaimDeviceRequest;
import com.hykon.iot_backend.dto.DeviceResponse;
import com.hykon.iot_backend.entity.*;
import com.hykon.iot_backend.repository.DeviceCustomerAccessRepository;
import com.hykon.iot_backend.repository.DevicePurchaseRepository;
import com.hykon.iot_backend.repository.DeviceRepository;
import com.hykon.iot_backend.repository.UserRepository;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class DeviceClaimService {

    private final UserRepository userRepository;
    private final DeviceRepository deviceRepository;
    private final DevicePurchaseRepository purchaseRepository;
    private final DeviceCustomerAccessRepository customerAccessRepository;

    @Autowired
    public DeviceClaimService(
            UserRepository userRepository,
            DeviceRepository deviceRepository,
            DevicePurchaseRepository purchaseRepository,
            DeviceCustomerAccessRepository customerAccessRepository
    ) {
        this.userRepository = userRepository;
        this.deviceRepository = deviceRepository;
        this.purchaseRepository = purchaseRepository;
        this.customerAccessRepository = customerAccessRepository;
    }

    @Transactional
    public DeviceResponse claim(
            ClaimDeviceRequest request,
            String customerEmail
    ) {
        if (customerEmail == null || customerEmail.isBlank()) {
            throw new AccessDeniedException("USER authentication is required");
        }

        User customer = userRepository.findByEmailWithRole(customerEmail)
                .orElseThrow(() ->
                        new AccessDeniedException("USER authentication is required"));

        if (!"ACTIVE".equalsIgnoreCase(customer.getStatus())
                || customer.getRole() == null
                || customer.getRole().getName() != RoleName.USER) {
            throw new AccessDeniedException("USER authentication is required");
        }

        if (request.getDeviceCode() == null
                || request.getDeviceCode().isBlank()
                || request.getSerialNumber() == null
                || request.getSerialNumber().isBlank()) {
            throw verificationFailed();
        }

        Device device = deviceRepository.findByCodeForUpdate(
                request.getDeviceCode().trim()
        ).orElseThrow(this::verificationFailed);

        if (!"ACTIVE".equalsIgnoreCase(device.getStatus())
                || device.getSerialNumber() == null
                || !device.getSerialNumber().trim().equalsIgnoreCase(
                        request.getSerialNumber().trim()
                )) {
            throw verificationFailed();
        }

        DistributorBranch branch = device.getBranch();
        Distributor distributor =
                branch == null ? null : branch.getDistributor();

        if (branch == null
                || distributor == null
                || !"ACTIVE".equalsIgnoreCase(branch.getStatus())
                || !"ACTIVE".equalsIgnoreCase(distributor.getStatus())) {
            throw verificationFailed();
        }

        // 1. Check if customer is already authorized in access table (idempotent repeated verification)
        if (customerAccessRepository.existsByDeviceIdAndCustomerId(device.getId(), customer.getId())) {
            List<String> emails = getCustomerEmails(device.getId(), customer);
            return DeviceResponse.from(device, emails);
        }

        // If device is already owned by another customer, reject immediately before querying purchase
        if (device.getOwner() != null && !customer.getId().equals(device.getOwner().getId())) {
            throw verificationFailed();
        }

        // 2. Check recorded purchase for primary owner claim
        DevicePurchase purchase = purchaseRepository.findByDeviceIdForUpdate(
                device.getId()
        ).orElseThrow(this::verificationFailed);

        if (purchase.getCustomer() == null
                || !customer.getId().equals(purchase.getCustomer().getId())) {
            throw verificationFailed();
        }

        if (device.getOwner() != null) {
            if (!customer.getId().equals(device.getOwner().getId())) {
                throw verificationFailed();
            }

            if (!"CLAIMED".equals(purchase.getStatus())
                    || purchase.getClaimedAt() == null) {
                throw verificationFailed();
            }

            if (customerAccessRepository != null && !customerAccessRepository.existsByDeviceIdAndCustomerId(device.getId(), customer.getId())) {
                DeviceCustomerAccess access = new DeviceCustomerAccess(device, customer, true, customer);
                customerAccessRepository.saveAndFlush(access);
            }

            List<String> emails = getCustomerEmails(device.getId(), customer);
            return DeviceResponse.from(device, emails);
        }

        if (!"ELIGIBLE".equals(purchase.getStatus())
                || purchase.getClaimedAt() != null) {
            throw verificationFailed();
        }

        device.setOwner(customer);
        purchase.setStatus("CLAIMED");
        purchase.setClaimedAt(LocalDateTime.now());

        Device savedDevice = deviceRepository.saveAndFlush(device);
        purchaseRepository.saveAndFlush(purchase);

        if (customerAccessRepository != null) {
            DeviceCustomerAccess access = new DeviceCustomerAccess(savedDevice, customer, true, customer);
            customerAccessRepository.saveAndFlush(access);
        }

        List<String> emails = getCustomerEmails(savedDevice.getId(), customer);
        return DeviceResponse.from(savedDevice, emails);
    }

    private List<String> getCustomerEmails(Long deviceId, User defaultCustomer) {
        List<DeviceCustomerAccess> accesses = customerAccessRepository.findByDeviceIdWithCustomer(deviceId);
        if (!accesses.isEmpty()) {
            return accesses.stream().map(a -> a.getCustomer().getEmail()).toList();
        }
        return defaultCustomer != null && defaultCustomer.getEmail() != null
                ? List.of(defaultCustomer.getEmail())
                : List.of();
    }

    private IllegalArgumentException verificationFailed() {
        return new IllegalArgumentException("Device verification failed");
    }
}