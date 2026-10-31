package com.hykon.iot_backend.service;

import com.hykon.iot_backend.dto.CreateDistributorRequest;
import com.hykon.iot_backend.dto.DistributorDirectoryResponse;
import com.hykon.iot_backend.dto.DistributorResponse;
import com.hykon.iot_backend.entity.Device;
import com.hykon.iot_backend.entity.Distributor;
import com.hykon.iot_backend.entity.DistributorBranch;
import com.hykon.iot_backend.entity.RoleName;
import com.hykon.iot_backend.entity.User;
import com.hykon.iot_backend.exception.DuplicateResourceException;
import com.hykon.iot_backend.repository.DeviceCustomerAccessRepository;
import com.hykon.iot_backend.repository.DeviceRepository;
import com.hykon.iot_backend.repository.DistributorBranchRepository;
import com.hykon.iot_backend.repository.DistributorRepository;
import com.hykon.iot_backend.repository.UserRepository;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

@Service
public class DistributorService {

    private final UserRepository userRepository;
    private final DistributorRepository distributorRepository;
    private final DistributorBranchRepository branchRepository;
    private final DeviceRepository deviceRepository;
    private final DeviceCustomerAccessRepository customerAccessRepository;

    @Autowired
    public DistributorService(
            UserRepository userRepository,
            DistributorRepository distributorRepository,
            DistributorBranchRepository branchRepository,
            DeviceRepository deviceRepository,
            DeviceCustomerAccessRepository customerAccessRepository
    ) {
        this.userRepository = userRepository;
        this.distributorRepository = distributorRepository;
        this.branchRepository = branchRepository;
        this.deviceRepository = deviceRepository;
        this.customerAccessRepository = customerAccessRepository;
    }

    @Transactional(readOnly = true)
    public List<DistributorDirectoryResponse> getDirectory(String adminEmail) {
        return getDirectory(adminEmail, 0, 50, 20, 25);
    }

    @Transactional(readOnly = true)
    public List<DistributorDirectoryResponse> getDirectory(
            String adminEmail,
            int page,
            int size,
            int branchLimit,
            int deviceLimit
    ) {
        requireHykonAdmin(adminEmail);

        int boundedPage = Math.max(page, 0);
        int boundedSize = Math.min(Math.max(size, 1), 50);
        int bLimit = Math.min(Math.max(branchLimit, 1), 100);
        int dLimit = Math.min(Math.max(deviceLimit, 1), 100);

        org.springframework.data.domain.Page<Distributor> distPage = distributorRepository.findAll(
                org.springframework.data.domain.PageRequest.of(boundedPage, boundedSize, org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.ASC, "id"))
        );
        List<Distributor> distributors = distPage.getContent();
        if (distributors.isEmpty()) {
            return List.of();
        }

        List<Long> distributorIds = distributors.stream().map(Distributor::getId).toList();
        List<DistributorBranch> allBranches = branchRepository.findByDistributor_IdIn(distributorIds);

        List<Long> branchIds = allBranches.stream().map(DistributorBranch::getId).toList();
        List<Device> allDevices = (deviceRepository != null && !branchIds.isEmpty())
                ? deviceRepository.findByBranch_IdIn(branchIds)
                : List.of();

        List<Long> deviceIds = allDevices.stream().map(Device::getId).toList();
        java.util.Map<Long, List<String>> emailsByDevice = new java.util.HashMap<>();
        if (customerAccessRepository != null && !deviceIds.isEmpty()) {
            customerAccessRepository.findByDeviceIdInWithCustomer(deviceIds).forEach(access -> {
                emailsByDevice.computeIfAbsent(access.getDevice().getId(), k -> new java.util.ArrayList<>())
                        .add(access.getCustomer().getEmail());
            });
        }

        return distributors.stream().map(dist -> {
            User administrator = dist.getAdministrator();
            DistributorDirectoryResponse.AdminSummary adminSummary = administrator == null ? null :
                    new DistributorDirectoryResponse.AdminSummary(
                            administrator.getId(),
                            administrator.getName(),
                            administrator.getEmail(),
                            administrator.getStatus()
                    );

            List<DistributorDirectoryResponse.BranchHierarchy> branchList = allBranches.stream()
                    .filter(b -> b.getDistributor() != null && dist.getId().equals(b.getDistributor().getId()))
                    .limit(bLimit)
                    .map(b -> {
                        List<DistributorDirectoryResponse.DeviceSummary> devList = allDevices.stream()
                                .filter(d -> d.getBranch() != null && b.getId().equals(d.getBranch().getId()))
                                .limit(dLimit)
                                .map(d -> {
                                    List<String> customerEmails = emailsByDevice.getOrDefault(d.getId(), List.of());
                                    if (customerEmails.isEmpty() && d.getOwner() != null && d.getOwner().getEmail() != null) {
                                        customerEmails = List.of(d.getOwner().getEmail());
                                    }
                                    return new DistributorDirectoryResponse.DeviceSummary(
                                            d.getId(),
                                            d.getDeviceCode(),
                                            d.getSerialNumber(),
                                            d.getSystemName(),
                                            d.getModel(),
                                            d.getStatus(),
                                            customerEmails
                                    );
                                }).toList();

                        return new DistributorDirectoryResponse.BranchHierarchy(
                                b.getId(),
                                b.getBranchCode(),
                                b.getName(),
                                b.getLocation(),
                                b.getStatus(),
                                devList
                        );
                    }).toList();

            return new DistributorDirectoryResponse(
                    dist.getId(),
                    dist.getDistributorCode(),
                    dist.getName(),
                    dist.getStatus(),
                    dist.getCreatedAt(),
                    adminSummary,
                    branchList
            );
        }).toList();
    }

    @Transactional(readOnly = true)
    public org.springframework.data.domain.Page<DistributorDirectoryResponse.BranchHierarchy> getDistributorBranches(
            Long distributorId,
            String adminEmail,
            int page,
            int size
    ) {
        requireHykonAdmin(adminEmail);
        int boundedPage = Math.max(page, 0);
        int boundedSize = Math.min(Math.max(size, 1), 50);

        org.springframework.data.domain.Page<DistributorBranch> branches = branchRepository.findByDistributor_Id(
                distributorId, org.springframework.data.domain.PageRequest.of(boundedPage, boundedSize, org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.ASC, "id"))
        );

        List<Long> branchIds = branches.getContent().stream().map(DistributorBranch::getId).toList();
        List<Device> devices = (deviceRepository != null && !branchIds.isEmpty())
                ? deviceRepository.findByBranch_IdIn(branchIds)
                : List.of();

        List<Long> deviceIds = devices.stream().map(Device::getId).toList();
        java.util.Map<Long, List<String>> emailsByDevice = new java.util.HashMap<>();
        if (customerAccessRepository != null && !deviceIds.isEmpty()) {
            customerAccessRepository.findByDeviceIdInWithCustomer(deviceIds).forEach(access -> {
                emailsByDevice.computeIfAbsent(access.getDevice().getId(), k -> new java.util.ArrayList<>())
                        .add(access.getCustomer().getEmail());
            });
        }

        return branches.map(b -> {
            List<DistributorDirectoryResponse.DeviceSummary> devList = devices.stream()
                    .filter(d -> d.getBranch() != null && b.getId().equals(d.getBranch().getId()))
                    .map(d -> {
                        List<String> customerEmails = emailsByDevice.getOrDefault(d.getId(), List.of());
                        if (customerEmails.isEmpty() && d.getOwner() != null && d.getOwner().getEmail() != null) {
                            customerEmails = List.of(d.getOwner().getEmail());
                        }
                        return new DistributorDirectoryResponse.DeviceSummary(
                                d.getId(),
                                d.getDeviceCode(),
                                d.getSerialNumber(),
                                d.getSystemName(),
                                d.getModel(),
                                d.getStatus(),
                                customerEmails
                        );
                    }).toList();

            return new DistributorDirectoryResponse.BranchHierarchy(
                    b.getId(),
                    b.getBranchCode(),
                    b.getName(),
                    b.getLocation(),
                    b.getStatus(),
                    devList
            );
        });
    }

    @Transactional(readOnly = true)
    public org.springframework.data.domain.Page<DistributorDirectoryResponse.DeviceSummary> getBranchDevices(
            Long branchId,
            String adminEmail,
            int page,
            int size
    ) {
        requireHykonAdmin(adminEmail);
        int boundedPage = Math.max(page, 0);
        int boundedSize = Math.min(Math.max(size, 1), 50);

        org.springframework.data.domain.Page<Device> devices = deviceRepository.findByBranch_Id(
                branchId, org.springframework.data.domain.PageRequest.of(boundedPage, boundedSize, org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.ASC, "id"))
        );

        List<Long> deviceIds = devices.getContent().stream().map(Device::getId).toList();
        java.util.Map<Long, List<String>> emailsByDevice = new java.util.HashMap<>();
        if (customerAccessRepository != null && !deviceIds.isEmpty()) {
            customerAccessRepository.findByDeviceIdInWithCustomer(deviceIds).forEach(access -> {
                emailsByDevice.computeIfAbsent(access.getDevice().getId(), k -> new java.util.ArrayList<>())
                        .add(access.getCustomer().getEmail());
            });
        }

        return devices.map(d -> {
            List<String> customerEmails = emailsByDevice.getOrDefault(d.getId(), List.of());
            if (customerEmails.isEmpty() && d.getOwner() != null && d.getOwner().getEmail() != null) {
                customerEmails = List.of(d.getOwner().getEmail());
            }
            return new DistributorDirectoryResponse.DeviceSummary(
                    d.getId(),
                    d.getDeviceCode(),
                    d.getSerialNumber(),
                    d.getSystemName(),
                    d.getModel(),
                    d.getStatus(),
                    customerEmails
            );
        });
    }

    private User requireHykonAdmin(String email) {
        if (email == null || email.isBlank()) {
            throw new AccessDeniedException("HYKON_ADMIN access is required");
        }
        User admin = userRepository.findByEmailWithRole(email)
                .orElseThrow(() -> new AccessDeniedException("HYKON_ADMIN access is required"));
        if (!"ACTIVE".equalsIgnoreCase(admin.getStatus())
                || admin.getRole() == null
                || admin.getRole().getName() != RoleName.HYKON_ADMIN) {
            throw new AccessDeniedException("HYKON_ADMIN access is required");
        }
        return admin;
    }

    @Transactional
    public DistributorResponse create(
            CreateDistributorRequest request,
            String creatorEmail
    ) {
        if (creatorEmail == null || creatorEmail.isBlank()) {
            throw new AccessDeniedException("HYKON_ADMIN access is required");
        }

        User creator = userRepository.findByEmailWithRole(creatorEmail)
                .orElseThrow(() ->
                        new AccessDeniedException("HYKON_ADMIN access is required"));

        if (!"ACTIVE".equalsIgnoreCase(creator.getStatus())
                || creator.getRole() == null
                || creator.getRole().getName() != RoleName.HYKON_ADMIN) {
            throw new AccessDeniedException("HYKON_ADMIN access is required");
        }

        if (request.getAdministratorUserId() == null
                || request.getAdministratorUserId() <= 0) {
            throw new IllegalArgumentException(
                    "A valid distributor administrator account is required"
            );
        }

        User administrator = userRepository
                .findById(request.getAdministratorUserId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Distributor administrator account was not found"
                ));

        if (!"ACTIVE".equalsIgnoreCase(administrator.getStatus())
                || administrator.getRole() == null
                || administrator.getRole().getName()
                        != RoleName.DISTRIBUTOR_ADMIN) {
            throw new IllegalArgumentException(
                    "Select an active DISTRIBUTOR_ADMIN account"
            );
        }

        String distributorCode =
                requiredText(request.getDistributorCode(), "Distributor ID")
                        .toUpperCase(Locale.ROOT);

        String distributorName = requiredText(request.getName(), "Distributor name");
        String branchCode = requiredText(request.getBranchCode(), "Branch code")
                .toUpperCase(Locale.ROOT);
        String branchName = requiredText(request.getBranchName(), "Branch name");
        String location = requiredText(request.getLocation(), "Location");

        if (distributorRepository.findByCode(distributorCode).isPresent()) {
            throw new DuplicateResourceException(
                    "Distributor ID is already registered"
            );
        }

        if (distributorRepository.existsByAdministrator_Id(
                administrator.getId()
        )) {
            throw new DuplicateResourceException(
                    "This administrator is already assigned to a distributor"
            );
        }

        Distributor distributor = new Distributor();
        distributor.setDistributorCode(distributorCode);
        distributor.setName(distributorName);
        distributor.setAdministrator(administrator);
        distributor.setCreatedBy(creator);
        distributor.setStatus("ACTIVE");

        try {
            Distributor savedDistributor =
                    distributorRepository.saveAndFlush(distributor);

            DistributorBranch branch = new DistributorBranch();
            branch.setDistributor(savedDistributor);
            branch.setBranchCode(branchCode);
            branch.setName(branchName);
            branch.setLocation(location);
            branch.setStatus("ACTIVE");

            DistributorBranch savedBranch =
                    branchRepository.saveAndFlush(branch);

            return DistributorResponse.from(savedDistributor, savedBranch);
        }
        catch (DataIntegrityViolationException exception) {
            throw new DuplicateResourceException(
                    "Distributor details conflict with an existing database record"
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