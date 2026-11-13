package com.hykon.iot_backend.service;

import com.hykon.iot_backend.dto.CreateBranchWithAdminRequest;
import com.hykon.iot_backend.dto.DistributorDirectoryResponse;
import com.hykon.iot_backend.entity.Distributor;
import com.hykon.iot_backend.entity.DistributorBranch;
import com.hykon.iot_backend.entity.Role;
import com.hykon.iot_backend.entity.RoleName;
import com.hykon.iot_backend.entity.User;
import com.hykon.iot_backend.exception.DuplicateResourceException;
import com.hykon.iot_backend.repository.DistributorBranchRepository;
import com.hykon.iot_backend.repository.DistributorRepository;
import com.hykon.iot_backend.repository.RoleRepository;
import com.hykon.iot_backend.repository.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

/** Creates a branch together with its dedicated BRANCH_ADMIN login. */
@Service
public class BranchAdminService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final DistributorRepository distributorRepository;
    private final DistributorBranchRepository branchRepository;
    private final PasswordEncoder passwordEncoder;

    public BranchAdminService(
            UserRepository userRepository,
            RoleRepository roleRepository,
            DistributorRepository distributorRepository,
            DistributorBranchRepository branchRepository,
            PasswordEncoder passwordEncoder
    ) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.distributorRepository = distributorRepository;
        this.branchRepository = branchRepository;
        this.passwordEncoder = passwordEncoder;
    }

    /** Creates the BRANCH_ADMIN login for an existing branch that has none yet. */
    @Transactional
    public DistributorDirectoryResponse.BranchHierarchy assignAdmin(
            Long branchId,
            com.hykon.iot_backend.dto.AssignBranchAdminRequest request,
            String actorEmail
    ) {
        User actor = userRepository.findByEmailWithRole(actorEmail == null ? "" : actorEmail)
                .orElseThrow(() -> new AccessDeniedException("Administrator access is required"));

        if (!"ACTIVE".equalsIgnoreCase(actor.getStatus()) || actor.getRole() == null
                || (actor.getRole().getName() != RoleName.HYKON_ADMIN
                    && actor.getRole().getName() != RoleName.DISTRIBUTOR_ADMIN)) {
            throw new AccessDeniedException("Administrator access is required");
        }

        DistributorBranch branch = branchRepository.findById(branchId)
                .orElseThrow(() -> new IllegalArgumentException("Branch was not found"));

        if (!BranchAccess.canManage(actor, branch)) {
            throw new AccessDeniedException("Branch is unavailable");
        }
        if (branch.getBranchAdmin() != null) {
            throw new DuplicateResourceException("This branch already has a branch admin");
        }
        if (!request.getAdminPassword().equals(request.getConfirmPassword())) {
            throw new IllegalArgumentException("Passwords do not match");
        }

        String adminEmail = request.getAdminEmail().trim().toLowerCase(Locale.ROOT);
        if (userRepository.existsByEmail(adminEmail)) {
            throw new DuplicateResourceException("Email is already registered");
        }

        Role branchAdminRole = roleRepository.findByName(RoleName.BRANCH_ADMIN)
                .orElseThrow(() -> new IllegalStateException("BRANCH_ADMIN role is not configured"));

        User branchAdmin = new User();
        branchAdmin.setName(request.getAdminName().trim());
        branchAdmin.setEmail(adminEmail);
        branchAdmin.setPassword(passwordEncoder.encode(request.getAdminPassword()));
        branchAdmin.setRole(branchAdminRole);
        branchAdmin.setStatus("ACTIVE");

        try {
            branch.setBranchAdmin(userRepository.saveAndFlush(branchAdmin));
            DistributorBranch saved = branchRepository.saveAndFlush(branch);
            return new DistributorDirectoryResponse.BranchHierarchy(
                    saved.getId(), saved.getBranchCode(), saved.getName(),
                    saved.getLocation(), saved.getStatus(), List.of());
        } catch (DataIntegrityViolationException exception) {
            throw new DuplicateResourceException(
                    "Branch admin details conflict with an existing database record");
        }
    }

    /**
     * HYKON_ADMIN passes the target distributorId. A DISTRIBUTOR_ADMIN passes
     * null (their own distributor is used) or their own distributor id.
     */
    @Transactional
    public DistributorDirectoryResponse.BranchHierarchy create(
            Long distributorId,
            CreateBranchWithAdminRequest request,
            String actorEmail
    ) {
        User actor = userRepository.findByEmailWithRole(actorEmail == null ? "" : actorEmail)
                .orElseThrow(() -> new AccessDeniedException("Administrator access is required"));

        if (!"ACTIVE".equalsIgnoreCase(actor.getStatus()) || actor.getRole() == null) {
            throw new AccessDeniedException("Administrator access is required");
        }

        RoleName role = actor.getRole().getName();
        Distributor distributor;

        if (role == RoleName.HYKON_ADMIN) {
            if (distributorId == null) {
                throw new IllegalArgumentException("Distributor is required");
            }
            distributor = distributorRepository.findById(distributorId)
                    .orElseThrow(() -> new IllegalArgumentException("Distributor was not found"));
        } else if (role == RoleName.DISTRIBUTOR_ADMIN) {
            distributor = distributorRepository.findByAdministrator_Id(actor.getId())
                    .orElseThrow(() -> new AccessDeniedException("No distributor is assigned to this account"));
            if (distributorId != null && !distributorId.equals(distributor.getId())) {
                throw new AccessDeniedException("You can only create branches in your own distributor");
            }
        } else {
            throw new AccessDeniedException("Administrator access is required");
        }

        if (!"ACTIVE".equalsIgnoreCase(distributor.getStatus())) {
            throw new AccessDeniedException("Distributor is unavailable");
        }

        if (!request.getAdminPassword().equals(request.getConfirmPassword())) {
            throw new IllegalArgumentException("Passwords do not match");
        }

        String branchCode = request.getBranchCode().trim().toUpperCase(Locale.ROOT);
        String adminEmail = request.getAdminEmail().trim().toLowerCase(Locale.ROOT);

        if (branchRepository.findByDistributorAndCode(distributor.getId(), branchCode).isPresent()) {
            throw new DuplicateResourceException("Branch code is already used by this distributor");
        }
        if (userRepository.existsByEmail(adminEmail)) {
            throw new DuplicateResourceException("Email is already registered");
        }

        Role branchAdminRole = roleRepository.findByName(RoleName.BRANCH_ADMIN)
                .orElseThrow(() -> new IllegalStateException("BRANCH_ADMIN role is not configured"));

        User branchAdmin = new User();
        branchAdmin.setName(request.getAdminName().trim());
        branchAdmin.setEmail(adminEmail);
        branchAdmin.setPassword(passwordEncoder.encode(request.getAdminPassword()));
        branchAdmin.setRole(branchAdminRole);
        branchAdmin.setStatus("ACTIVE");

        try {
            User savedAdmin = userRepository.saveAndFlush(branchAdmin);

            DistributorBranch branch = new DistributorBranch();
            branch.setDistributor(distributor);
            branch.setBranchCode(branchCode);
            branch.setName(request.getBranchName().trim());
            branch.setLocation(request.getLocation().trim());
            branch.setPincode(request.getPincode() == null || request.getPincode().isBlank() ? null : request.getPincode().trim());
            branch.setStatus("ACTIVE");
            branch.setBranchAdmin(savedAdmin);

            DistributorBranch saved = branchRepository.saveAndFlush(branch);
            return new DistributorDirectoryResponse.BranchHierarchy(
                    saved.getId(), saved.getBranchCode(), saved.getName(),
                    saved.getLocation(), saved.getStatus(), List.of());
        } catch (DataIntegrityViolationException exception) {
            throw new DuplicateResourceException(
                    "Branch details conflict with an existing database record");
        }
    }
}
