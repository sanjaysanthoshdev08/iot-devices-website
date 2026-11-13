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
    private final com.hykon.iot_backend.repository.DistributorRepository distributorRepository;

    public DeviceRegistrationService(
            UserRepository userRepository,
            DeviceRepository deviceRepository,
            DistributorBranchRepository branchRepository,
            com.hykon.iot_backend.repository.DistributorRepository distributorRepository
    ) {
        this.distributorRepository = distributorRepository;
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
                && role != RoleName.DISTRIBUTOR_ADMIN
                && role != RoleName.BRANCH_ADMIN) {
            throw new AccessDeniedException("Administrator access is required");
        }

        if (role == RoleName.BRANCH_ADMIN && request.getBranchId() == null) {
            branchRepository.findByBranchAdmin_Id(creator.getId())
                    .ifPresent(own -> request.setBranchId(own.getId()));
        }

        String distCodeIn = request.getDistributorCode() == null ? "" : request.getDistributorCode().trim();
        String branchCodeIn = request.getBranchCode() == null ? "" : request.getBranchCode().trim();

        if (!distCodeIn.isEmpty() || !branchCodeIn.isEmpty()) {
            if (distCodeIn.isEmpty()) {
                throw new IllegalArgumentException("Distributor code is required");
            }
            if (branchCodeIn.isEmpty()) {
                throw new IllegalArgumentException("Branch code is required");
            }
            Distributor byCode = distributorRepository.findByCode(distCodeIn)
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Distributor code '" + distCodeIn + "' was not found"));
            DistributorBranch byBranch = branchRepository
                    .findByDistributorAndCode(byCode.getId(), branchCodeIn)
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Branch code '" + branchCodeIn
                                    + "' does not exist under distributor '" + distCodeIn + "'"));
            request.setBranchId(byBranch.getId());
        } else if (request.getBranchId() == null && role != RoleName.BRANCH_ADMIN) {
            throw new IllegalArgumentException("Distributor code and branch code are required");
        }

        DistributorBranch branch = null;

        if (request.getBranchId() == null) {
            if (role != RoleName.HYKON_ADMIN) {
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

            if (role == RoleName.BRANCH_ADMIN
                    && !BranchAccess.canManage(creator, branch)) {
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
                    "This system is already registered"
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

        // 1. Validate serial number: YY + MM + 5-digit running number (exactly 9 digits).
        //    The running number restarts every month, e.g. 261100001, 261100002 ... 261200001.
        if (!sNum.matches("^\\d{9}$")) {
            throw new IllegalArgumentException(
                    "Serial number must be 9 digits in format YYMM + 5-digit device number (e.g. 261100001)"
            );
        }
        int month = Integer.parseInt(sNum.substring(2, 4));
        if (month < 1 || month > 12) {
            throw new IllegalArgumentException(
                    "Serial number month must be between 01 and 12"
            );
        }
        int serialDeviceNumber = Integer.parseInt(sNum.substring(4, 9));
        if (serialDeviceNumber < 1) {
            throw new IllegalArgumentException(
                    "Device number must be between 00001 and 99999"
            );
        }

        // 2. Validate Device ID: <model>-<number>. It is independent of the serial number,
        //    because serial running numbers repeat across months while Device IDs must stay unique.
        if (!dCode.matches("^[A-Za-z0-9]+-\\d+$")) {
            throw new IllegalArgumentException(
                    "Device ID must follow format <model>-<number> (e.g. halo1200-1)"
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