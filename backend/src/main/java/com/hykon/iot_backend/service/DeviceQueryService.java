package com.hykon.iot_backend.service;

import com.hykon.iot_backend.dto.DeviceResponse;
import com.hykon.iot_backend.entity.Device;
import com.hykon.iot_backend.entity.Distributor;
import com.hykon.iot_backend.entity.RoleName;
import com.hykon.iot_backend.entity.User;
import com.hykon.iot_backend.repository.DeviceRepository;
import com.hykon.iot_backend.repository.DistributorRepository;
import com.hykon.iot_backend.repository.UserRepository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional(readOnly = true)
public class DeviceQueryService {

    private final UserRepository userRepository;
    private final DeviceRepository deviceRepository;
    private final DistributorRepository distributorRepository;

    public DeviceQueryService(
            UserRepository userRepository,
            DeviceRepository deviceRepository,
            DistributorRepository distributorRepository
    ) {
        this.userRepository = userRepository;
        this.deviceRepository = deviceRepository;
        this.distributorRepository = distributorRepository;
    }

    public Page<DeviceResponse> list(
            String email,
            int page,
            int size
    ) {
        User account = requireActiveAccount(email);

        if (page < 0 || size < 1 || size > 100) {
            throw new IllegalArgumentException(
                    "Page must be at least 0 and size must be between 1 and 100"
            );
        }

        Pageable pageable = PageRequest.of(
                page, size, Sort.by(Sort.Direction.DESC, "id")
        );

        Page<Device> devices;

        switch (account.getRole().getName()) {
            case HYKON_ADMIN ->
                    devices = deviceRepository.findAll(pageable);

            case DISTRIBUTOR_ADMIN -> {
                requireActiveDistributor(account);
                devices = deviceRepository
                        .findByBranch_Distributor_Administrator_Id(
                                account.getId(), pageable
                        );
            }

            case USER ->
                    devices = deviceRepository.findByOwner_Id(
                            account.getId(), pageable
                    );

            default -> throw forbidden();
        }

        return devices.map(DeviceResponse::from);
    }

    public DeviceResponse get(
            Long deviceId,
            String email
    ) {
        User account = requireActiveAccount(email);

        if (deviceId == null || deviceId <= 0) {
            throw new IllegalArgumentException(
                    "Device ID must be a positive number"
            );
        }

        Device device;

        switch (account.getRole().getName()) {
            case HYKON_ADMIN ->
                    device = deviceRepository.findById(deviceId)
                            .orElseThrow(this::notFound);

            case DISTRIBUTOR_ADMIN -> {
                requireActiveDistributor(account);
                device = deviceRepository
                        .findByIdAndBranch_Distributor_Administrator_Id(
                                deviceId, account.getId()
                        )
                        .orElseThrow(this::notFound);
            }

            case USER ->
                    device = deviceRepository.findByIdAndOwner_Id(
                            deviceId, account.getId()
                    ).orElseThrow(this::notFound);

            default -> throw forbidden();
        }

        return DeviceResponse.from(device);
    }

    private User requireActiveAccount(String email) {
        if (email == null || email.isBlank()) {
            throw forbidden();
        }

        User account = userRepository.findByEmailWithRole(email)
                .orElseThrow(this::forbidden);

        if (!"ACTIVE".equalsIgnoreCase(account.getStatus())
                || account.getRole() == null
                || account.getRole().getName() == null) {
            throw forbidden();
        }

        return account;
    }

    private void requireActiveDistributor(User account) {
        Distributor distributor = distributorRepository
                .findByAdministrator_Id(account.getId())
                .orElseThrow(this::forbidden);

        if (!"ACTIVE".equalsIgnoreCase(distributor.getStatus())) {
            throw forbidden();
        }
    }

    private ResponseStatusException forbidden() {
        return new ResponseStatusException(
                HttpStatus.FORBIDDEN,
                "You do not have permission to access this resource"
        );
    }

    private ResponseStatusException notFound() {
        return new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "Device not found"
        );
    }
}