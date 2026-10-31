package com.hykon.iot_backend.service;

import com.hykon.iot_backend.dto.DeviceResponse;
import com.hykon.iot_backend.dto.RegisterDeviceRequest;
import com.hykon.iot_backend.entity.*;
import com.hykon.iot_backend.exception.DuplicateResourceException;
import com.hykon.iot_backend.repository.DeviceRepository;
import com.hykon.iot_backend.repository.DistributorBranchRepository;
import com.hykon.iot_backend.repository.UserRepository;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
public class DeviceRegistrationService {

    private final UserRepository userRepository;
    private final DeviceRepository deviceRepository;
    private final DistributorBranchRepository branchRepository;

    public DeviceRegistrationService(
            UserRepository userRepository,
            DeviceRepository deviceRepository,
            DistributorBranchRepository branchRepository
    ) {
        this.userRepository = userRepository;
        this.deviceRepository = deviceRepository;
        this.branchRepository = branchRepository;
    }

    @Transactional
    public DeviceResponse register(
            RegisterDeviceRequest request,
            String creatorEmail
    ) {
        if (creatorEmail == null || creatorEmail.isBlank()) {
            throw new AccessDeniedException("Administrator access is required");
        }

        User creator = userRepository.findByEmailWithRole(creatorEmail)
                .orElseThrow(() ->
                        new AccessDeniedException("Administrator access is required"));

        if (!"ACTIVE".equalsIgnoreCase(creator.getStatus())
                || creator.getRole() == null
                || creator.getRole().getName() == null) {
            throw new AccessDeniedException("Administrator access is required");
        }

        RoleName role = creator.getRole().getName();

        if (role != RoleName.HYKON_ADMIN
                && role != RoleName.DISTRIBUTOR_ADMIN) {
            throw new AccessDeniedException("Administrator access is required");
        }

        DistributorBranch branch = null;

        if (request.getBranchId() == null) {
            if (role == RoleName.DISTRIBUTOR_ADMIN) {
                throw new AccessDeniedException(
                        "An authorized distributor branch is required"
                );
            }
        }
        else {
            if (request.getBranchId() <= 0) {
                throw new IllegalArgumentException("Branch ID must be positive");
            }

            branch = branchRepository.findById(request.getBranchId())
                    .orElseThrow(() ->
                            new AccessDeniedException("Branch is unavailable"));

            Distributor distributor = branch.getDistributor();

            if (distributor == null
                    || !"ACTIVE".equalsIgnoreCase(branch.getStatus())
                    || !"ACTIVE".equalsIgnoreCase(distributor.getStatus())) {
                throw new AccessDeniedException("Branch is unavailable");
            }

            if (role == RoleName.DISTRIBUTOR_ADMIN
                    && (distributor.getAdministrator() == null
                    || !creator.getId().equals(
                            distributor.getAdministrator().getId()
                    ))) {
                throw new AccessDeniedException("Branch is unavailable");
            }
        }

        String rawDeviceCode = requiredText(
                request.getDeviceCode(), "Device ID"
        );

        String rawSerialNumber = requiredText(
                request.getSerialNumber(), "Serial number"
        );

        validateDeviceCredentialsFormat(rawDeviceCode, rawSerialNumber);

        String deviceCode = rawDeviceCode.trim();
        String serialNumber = rawSerialNumber.trim();

        String systemName = requiredText(
                request.getSystemName(), "System name"
        );

        if (deviceRepository.findByCode(deviceCode).isPresent()
                || deviceRepository.findBySerialNumberNormalized(
                        serialNumber
                ).isPresent()) {
            throw new DuplicateResourceException(
                    "Device ID or serial number is already registered"
            );
        }

        Device device = new Device();
        device.setDeviceCode(deviceCode);
        device.setSerialNumber(serialNumber);
        device.setSystemName(systemName);
        device.setModel(
                request.getModel() == null || request.getModel().isBlank()
                        ? null : request.getModel().trim()
        );
        device.setBranch(branch);
        device.setCreatedBy(creator);
        device.setStatus("ACTIVE");

        // Ownership is assigned separately through verified purchase handling.
        device.setOwner(null);

        try {
            return DeviceResponse.from(
                    deviceRepository.saveAndFlush(device)
            );
        }
        catch (DataIntegrityViolationException exception) {
            throw new DuplicateResourceException(
                    "Device details conflict with an existing database record"
            );
        }
    }

    public static void validateDeviceCredentialsFormat(String deviceCode, String serialNumber) {
        if (deviceCode == null || deviceCode.isBlank()) {
            throw new IllegalArgumentException("Device ID is required");
        }
        if (serialNumber == null || serialNumber.isBlank()) {
            throw new IllegalArgumentException("Serial number is required");
        }

        String dCode = deviceCode.trim();
        String sNum = serialNumber.trim();

        // 1. Validate serial number: YY + MM + 6-digit device number (exactly 10 digits)
        if (!sNum.matches("^\\d{10}$")) {
            throw new IllegalArgumentException(
                    "Serial number must be 10 digits in format YYMM + 6-digit device number (e.g. 2610000001)"
            );
        }
        int month = Integer.parseInt(sNum.substring(2, 4));
        if (month < 1 || month > 12) {
            throw new IllegalArgumentException(
                    "Serial number month must be between 01 and 12"
            );
        }
        String serialSuffix = sNum.substring(4, 10);
        int serialDeviceNumber = Integer.parseInt(serialSuffix);
        if (serialDeviceNumber < 1 || serialDeviceNumber > 100000) {
            throw new IllegalArgumentException(
                    "Device number must be between 000001 and 100000"
            );
        }

        // 2. Validate Device ID: <model>-<6-digit device number>
        int lastHyphen = dCode.lastIndexOf('-');
        if (lastHyphen <= 0 || lastHyphen == dCode.length() - 1) {
            throw new IllegalArgumentException(
                    "Device ID must follow format <model>-<6-digit device number> (e.g. halo1200-000001)"
            );
        }
        String deviceCodeSuffix = dCode.substring(lastHyphen + 1);
        if (!deviceCodeSuffix.matches("^\\d{6}$")) {
            throw new IllegalArgumentException(
                    "Device ID suffix must be exactly 6 digits (e.g. halo1200-000001)"
            );
        }
        int deviceCodeNum = Integer.parseInt(deviceCodeSuffix);
        if (deviceCodeNum < 1 || deviceCodeNum > 100000) {
            throw new IllegalArgumentException(
                    "Device ID number must be between 000001 and 100000"
            );
        }

        // 3. Ensure numeric suffix matches between Device ID and serial number
        if (!deviceCodeSuffix.equals(serialSuffix)) {
            throw new IllegalArgumentException(
                    "Device ID numeric suffix (" + deviceCodeSuffix + ") and serial number suffix (" + serialSuffix + ") must match"
            );
        }
    }

    private String requiredText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        return value.trim();
    }
}