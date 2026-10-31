package com.hykon.iot_backend.service;

import com.hykon.iot_backend.dto.DistributorAccessRequest;
import com.hykon.iot_backend.dto.DistributorAccessResponse;
import com.hykon.iot_backend.entity.Distributor;
import com.hykon.iot_backend.entity.DistributorBranch;
import com.hykon.iot_backend.entity.RoleName;
import com.hykon.iot_backend.entity.User;
import com.hykon.iot_backend.repository.DistributorBranchRepository;
import com.hykon.iot_backend.repository.DistributorRepository;
import com.hykon.iot_backend.repository.UserRepository;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional(readOnly = true)
public class DistributorAccessService {

    private final UserRepository userRepository;
    private final DistributorRepository distributorRepository;
    private final DistributorBranchRepository branchRepository;

    public DistributorAccessService(
            UserRepository userRepository,
            DistributorRepository distributorRepository,
            DistributorBranchRepository branchRepository
    ) {
        this.userRepository = userRepository;
        this.distributorRepository = distributorRepository;
        this.branchRepository = branchRepository;
    }

    public DistributorAccessResponse verify(
            DistributorAccessRequest request,
            String email
    ) {
        if (email == null || email.isBlank()) {
            throw forbidden();
        }

        User administrator = userRepository.findByEmailWithRole(email)
                .orElseThrow(this::forbidden);

        if (!"ACTIVE".equalsIgnoreCase(administrator.getStatus())
                || administrator.getRole() == null
                || administrator.getRole().getName()
                        != RoleName.DISTRIBUTOR_ADMIN) {
            throw forbidden();
        }

        if (request == null || isBlank(request.getLocation())) {
            throw verificationFailed();
        }

        // Check for blank code in code mode
        if (isBlank(request.getDistributorCode()) || isBlank(request.getBranchCode())) {
            // But if name mode is present and valid, allow it
            if (isBlank(request.getDistributorName()) || isBlank(request.getBranchName())) {
                throw verificationFailed();
            }
        }

        Distributor distributor = distributorRepository
                .findByAdministrator_Id(administrator.getId())
                .orElseThrow(this::verificationFailed);

        if (!"ACTIVE".equalsIgnoreCase(distributor.getStatus())) {
            throw verificationFailed();
        }

        DistributorBranch branch;

        boolean isNameMode = !isBlank(request.getDistributorName()) && !isBlank(request.getBranchName());

        if (isNameMode) {
            if (!matches(distributor.getName(), request.getDistributorName())) {
                throw verificationFailed();
            }

            java.util.List<DistributorBranch> matchingBranches = branchRepository
                    .findByDistributorAndNameAndLocation(
                            distributor.getId(),
                            request.getBranchName().trim(),
                            request.getLocation().trim()
                    );

            if (matchingBranches.isEmpty()) {
                throw verificationFailed();
            }

            // Reject ambiguous matches safely
            if (matchingBranches.size() > 1) {
                throw verificationFailed();
            }

            branch = matchingBranches.get(0);
        } else {
            if (!matches(
                    distributor.getDistributorCode(),
                    request.getDistributorCode()
            )) {
                throw verificationFailed();
            }

            branch = branchRepository
                    .findByDistributorAndCode(
                            distributor.getId(),
                            request.getBranchCode().trim()
                    )
                    .orElseThrow(this::verificationFailed);

            if (!matches(branch.getLocation(), request.getLocation())) {
                throw verificationFailed();
            }
        }

        if (!"ACTIVE".equalsIgnoreCase(branch.getStatus())
                || branch.getDistributor() == null
                || distributor.getId() == null
                || !distributor.getId().equals(
                        branch.getDistributor().getId()
                )) {
            throw verificationFailed();
        }

        return new DistributorAccessResponse(
                distributor.getId(),
                distributor.getDistributorCode(),
                distributor.getName(),
                branch.getId(),
                branch.getBranchCode(),
                branch.getName(),
                branch.getLocation()
        );
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private boolean matches(String stored, String supplied) {
        return stored != null
                && supplied != null
                && stored.trim().equalsIgnoreCase(supplied.trim());
    }

    private ResponseStatusException forbidden() {
        return new ResponseStatusException(
                HttpStatus.FORBIDDEN,
                "DISTRIBUTOR_ADMIN authentication is required"
        );
    }

    private IllegalArgumentException verificationFailed() {
        return new IllegalArgumentException(
                "Distributor verification failed"
        );
    }
}