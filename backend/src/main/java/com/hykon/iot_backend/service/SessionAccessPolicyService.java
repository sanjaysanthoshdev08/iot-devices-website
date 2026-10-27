package com.hykon.iot_backend.service;

import com.hykon.iot_backend.entity.Device;
import com.hykon.iot_backend.entity.Distributor;
import com.hykon.iot_backend.entity.DistributorBranch;
import com.hykon.iot_backend.entity.RoleName;
import com.hykon.iot_backend.entity.SessionAccessGrant;
import com.hykon.iot_backend.entity.User;
import com.hykon.iot_backend.repository.SessionAccessGrantRepository;
import com.hykon.iot_backend.repository.UserRepository;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class SessionAccessPolicyService {

    private final UserRepository userRepository;
    private final LoginSessionService sessionService;
    private final SessionAccessGrantRepository grantRepository;

    public SessionAccessPolicyService(
            UserRepository userRepository,
            LoginSessionService sessionService,
            SessionAccessGrantRepository grantRepository
    ) {
        this.userRepository = userRepository;
        this.sessionService = sessionService;
        this.grantRepository = grantRepository;
    }

    public void requireVerified(Authentication authentication) {
        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication.getName() == null
                || authentication.getName().isBlank()
                || !(authentication.getDetails() instanceof UUID sessionId)) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "An authenticated login session is required"
            );
        }

        User account = userRepository
                .findByEmailWithRole(authentication.getName())
                .orElseThrow(this::verificationRequired);

        if (!active(account.getStatus())
                || account.getId() == null
                || account.getRole() == null
                || account.getRole().getName() == null) {
            throw verificationRequired();
        }

        sessionService.requireActive(sessionId, account.getId());

        RoleName role = account.getRole().getName();

        // Company administrators do not use customer/distributor gates.
        if (role == RoleName.HYKON_ADMIN) {
            return;
        }

        SessionAccessGrant grant = grantRepository.findById(sessionId)
                .orElseThrow(this::verificationRequired);

        boolean verified = switch (role) {
            case USER -> validDeviceGrant(grant, account.getId());
            case DISTRIBUTOR_ADMIN ->
                    validDistributorGrant(grant, account.getId());
            default -> false;
        };

        if (!verified) {
            throw verificationRequired();
        }
    }

    private boolean validDeviceGrant(
            SessionAccessGrant grant,
            Long userId
    ) {
        Device device = grant.getDevice();

        if (grant.getDeviceVerifiedAt() == null
                || device == null
                || !active(device.getStatus())
                || device.getOwner() == null
                || !userId.equals(device.getOwner().getId())) {
            return false;
        }

        DistributorBranch branch = device.getBranch();
        Distributor distributor =
                branch == null ? null : branch.getDistributor();

        return branch != null
                && active(branch.getStatus())
                && distributor != null
                && active(distributor.getStatus());
    }

    private boolean validDistributorGrant(
            SessionAccessGrant grant,
            Long administratorId
    ) {
        DistributorBranch branch = grant.getBranch();
        Distributor distributor =
                branch == null ? null : branch.getDistributor();

        return grant.getDistributorVerifiedAt() != null
                && branch != null
                && active(branch.getStatus())
                && distributor != null
                && active(distributor.getStatus())
                && distributor.getAdministrator() != null
                && administratorId.equals(
                        distributor.getAdministrator().getId()
                );
    }

    private boolean active(String status) {
        return "ACTIVE".equalsIgnoreCase(status);
    }

    private ResponseStatusException verificationRequired() {
        return new ResponseStatusException(
                HttpStatus.FORBIDDEN,
                "Access verification is required"
        );
    }
}