package com.hykon.iot_backend.service;

import com.hykon.iot_backend.dto.CreateDistributorRequest;
import com.hykon.iot_backend.dto.DistributorResponse;
import com.hykon.iot_backend.entity.Distributor;
import com.hykon.iot_backend.entity.DistributorBranch;
import com.hykon.iot_backend.entity.RoleName;
import com.hykon.iot_backend.entity.User;
import com.hykon.iot_backend.exception.DuplicateResourceException;
import com.hykon.iot_backend.repository.DistributorBranchRepository;
import com.hykon.iot_backend.repository.DistributorRepository;
import com.hykon.iot_backend.repository.UserRepository;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
public class DistributorService {

    private final UserRepository userRepository;
    private final DistributorRepository distributorRepository;
    private final DistributorBranchRepository branchRepository;

    public DistributorService(
            UserRepository userRepository,
            DistributorRepository distributorRepository,
            DistributorBranchRepository branchRepository
    ) {
        this.userRepository = userRepository;
        this.distributorRepository = distributorRepository;
        this.branchRepository = branchRepository;
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