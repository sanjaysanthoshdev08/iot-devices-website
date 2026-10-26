package com.hykon.iot_backend.service;

import com.hykon.iot_backend.dto.DevicePurchaseResponse;
import com.hykon.iot_backend.dto.RecordDevicePurchaseRequest;
import com.hykon.iot_backend.entity.*;
import com.hykon.iot_backend.exception.DuplicateResourceException;
import com.hykon.iot_backend.repository.DevicePurchaseRepository;
import com.hykon.iot_backend.repository.DeviceRepository;
import com.hykon.iot_backend.repository.UserRepository;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DevicePurchaseService {

    private final UserRepository userRepository;
    private final DeviceRepository deviceRepository;
    private final DevicePurchaseRepository purchaseRepository;

    public DevicePurchaseService(
            UserRepository userRepository,
            DeviceRepository deviceRepository,
            DevicePurchaseRepository purchaseRepository
    ) {
        this.userRepository = userRepository;
        this.deviceRepository = deviceRepository;
        this.purchaseRepository = purchaseRepository;
    }

    @Transactional
    public DevicePurchaseResponse record(
            RecordDevicePurchaseRequest request,
            String creatorEmail
    ) {
        if (creatorEmail == null || creatorEmail.isBlank()) {
            throw new AccessDeniedException("Administrator access is required");
        }

        User creator = userRepository.findByEmailWithRole(creatorEmail)
                .orElseThrow(() ->
                        new AccessDeniedException("Administrator access is required"));

        if (!isActiveRole(creator, RoleName.HYKON_ADMIN)
                && !isActiveRole(creator, RoleName.DISTRIBUTOR_ADMIN)) {
            throw new AccessDeniedException("Administrator access is required");
        }

        if (request.getDeviceId() == null || request.getDeviceId() <= 0
                || request.getCustomerUserId() == null
                || request.getCustomerUserId() <= 0) {
            throw new IllegalArgumentException(
                    "Valid device and customer account IDs are required"
            );
        }

        Device device = deviceRepository.findByIdForUpdate(
                request.getDeviceId()
        ).orElseThrow(() ->
                new AccessDeniedException("Device is unavailable"));

        DistributorBranch branch = device.getBranch();
        Distributor distributor =
                branch == null ? null : branch.getDistributor();

        if (!"ACTIVE".equalsIgnoreCase(device.getStatus())
                || branch == null
                || distributor == null
                || !"ACTIVE".equalsIgnoreCase(branch.getStatus())
                || !"ACTIVE".equalsIgnoreCase(distributor.getStatus())) {
            throw new AccessDeniedException("Device is unavailable");
        }

        if (creator.getRole().getName() == RoleName.DISTRIBUTOR_ADMIN
                && (distributor.getAdministrator() == null
                || !creator.getId().equals(
                        distributor.getAdministrator().getId()
                ))) {
            throw new AccessDeniedException("Device is unavailable");
        }

        if (device.getOwner() != null) {
            throw new DuplicateResourceException(
                    "Device already has an owner"
            );
        }

        if (purchaseRepository.existsByDevice_Id(device.getId())) {
            throw new DuplicateResourceException(
                    "A purchase record already exists for this device"
            );
        }

        User customer = userRepository.findById(
                request.getCustomerUserId()
        ).orElseThrow(() -> new IllegalArgumentException(
                "Customer account is unavailable"
        ));

        if (!isActiveRole(customer, RoleName.USER)) {
            throw new IllegalArgumentException(
                    "Select an active USER customer account"
            );
        }

        if (request.getPurchaseReference() == null
                || request.getPurchaseReference().isBlank()) {
            throw new IllegalArgumentException(
                    "Purchase reference is required"
            );
        }

        DevicePurchase purchase = new DevicePurchase();
        purchase.setDevice(device);
        purchase.setCustomer(customer);
        purchase.setRecordedBy(creator);
        purchase.setPurchaseReference(
                request.getPurchaseReference().trim()
        );
        purchase.setStatus("ELIGIBLE");
        purchase.setClaimedAt(null);

        try {
            return DevicePurchaseResponse.from(
                    purchaseRepository.saveAndFlush(purchase)
            );
        }
        catch (DataIntegrityViolationException exception) {
            throw new DuplicateResourceException(
                    "Purchase details conflict with an existing database record"
            );
        }
    }

    private boolean isActiveRole(User user, RoleName role) {
        return user != null
                && "ACTIVE".equalsIgnoreCase(user.getStatus())
                && user.getRole() != null
                && user.getRole().getName() == role;
    }
}