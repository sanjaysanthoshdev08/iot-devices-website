package com.hykon.iot_backend.service;

import com.hykon.iot_backend.dto.ClaimDeviceRequest;
import com.hykon.iot_backend.dto.DeviceResponse;
import com.hykon.iot_backend.entity.*;
import com.hykon.iot_backend.repository.DevicePurchaseRepository;
import com.hykon.iot_backend.repository.DeviceRepository;
import com.hykon.iot_backend.repository.UserRepository;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class DeviceClaimService {

    private final UserRepository userRepository;
    private final DeviceRepository deviceRepository;
    private final DevicePurchaseRepository purchaseRepository;

    public DeviceClaimService(
            UserRepository userRepository,
            DeviceRepository deviceRepository,
            DevicePurchaseRepository purchaseRepository
    ) {
        this.userRepository = userRepository;
        this.deviceRepository = deviceRepository;
        this.purchaseRepository = purchaseRepository;
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

        if (device.getOwner() != null
                && !customer.getId().equals(device.getOwner().getId())) {
            throw verificationFailed();
        }

        DevicePurchase purchase = purchaseRepository.findByDeviceIdForUpdate(
                device.getId()
        ).orElseThrow(this::verificationFailed);

        if (purchase.getCustomer() == null
                || !customer.getId().equals(purchase.getCustomer().getId())) {
            throw verificationFailed();
        }

        if (device.getOwner() != null) {
            if (!"CLAIMED".equals(purchase.getStatus())
                    || purchase.getClaimedAt() == null) {
                throw verificationFailed();
            }

            return DeviceResponse.from(device);
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

        return DeviceResponse.from(savedDevice);
    }

    private IllegalArgumentException verificationFailed() {
        return new IllegalArgumentException("Device verification failed");
    }
}