package com.hykon.iot_backend.service;

import com.hykon.iot_backend.dto.ClaimDeviceRequest;
import com.hykon.iot_backend.dto.DeviceResponse;
import com.hykon.iot_backend.dto.DistributorAccessRequest;
import com.hykon.iot_backend.dto.DistributorAccessResponse;
import com.hykon.iot_backend.entity.Device;
import com.hykon.iot_backend.entity.DistributorBranch;
import com.hykon.iot_backend.entity.RoleName;
import com.hykon.iot_backend.entity.SessionAccessGrant;
import com.hykon.iot_backend.entity.User;
import com.hykon.iot_backend.repository.DeviceRepository;
import com.hykon.iot_backend.repository.DistributorBranchRepository;
import com.hykon.iot_backend.repository.SessionAccessGrantRepository;
import com.hykon.iot_backend.repository.UserRepository;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@Transactional
public class SessionAccessVerificationService {

    private final UserRepository userRepository;
    private final LoginSessionService sessionService;
    private final SessionAccessGrantRepository grantRepository;
    private final DeviceClaimService deviceClaimService;
    private final DistributorAccessService distributorAccessService;
    private final DeviceRepository deviceRepository;
    private final DistributorBranchRepository branchRepository;

    public SessionAccessVerificationService(
            UserRepository userRepository,
            LoginSessionService sessionService,
            SessionAccessGrantRepository grantRepository,
            DeviceClaimService deviceClaimService,
            DistributorAccessService distributorAccessService,
            DeviceRepository deviceRepository,
            DistributorBranchRepository branchRepository
    ) {
        this.userRepository = userRepository;
        this.sessionService = sessionService;
        this.grantRepository = grantRepository;
        this.deviceClaimService = deviceClaimService;
        this.distributorAccessService = distributorAccessService;
        this.deviceRepository = deviceRepository;
        this.branchRepository = branchRepository;
    }

    public DeviceResponse verifyDevice(
            ClaimDeviceRequest request,
            String email,
            UUID sessionId
    ) {
        User account = requireAccount(email, RoleName.USER);

        sessionService.requireActiveForUpdate(
                sessionId, account.getId()
        );

        DeviceResponse response = deviceClaimService.claim(
                request, email
        );

        Device device = deviceRepository.findByIdAndOwner_Id(
                response.id(), account.getId()
        ).orElseThrow(() ->
                new IllegalArgumentException("Device verification failed"));

        SessionAccessGrant grant = getOrCreateGrant(sessionId);
        grant.setDevice(device);
        grant.setDeviceVerifiedAt(LocalDateTime.now());

        // Retain only verification for the account's current role.
        grant.setBranch(null);
        grant.setDistributorVerifiedAt(null);

        grantRepository.saveAndFlush(grant);

        return response;
    }

    public DistributorAccessResponse verifyDistributor(
            DistributorAccessRequest request,
            String email,
            UUID sessionId
    ) {
        User account = requireAccount(
                email, RoleName.DISTRIBUTOR_ADMIN
        );

        sessionService.requireActiveForUpdate(
                sessionId, account.getId()
        );

        DistributorAccessResponse response =
                distributorAccessService.verify(request, email);

        DistributorBranch branch = branchRepository
                .findByIdAndDistributor_Id(
                        response.branchId(),
                        response.distributorId()
                )
                .orElseThrow(() -> new IllegalArgumentException(
                        "Distributor verification failed"
                ));

        SessionAccessGrant grant = getOrCreateGrant(sessionId);
        grant.setBranch(branch);
        grant.setDistributorVerifiedAt(LocalDateTime.now());

        grant.setDevice(null);
        grant.setDeviceVerifiedAt(null);

        grantRepository.saveAndFlush(grant);

        return response;
    }

    private SessionAccessGrant getOrCreateGrant(UUID sessionId) {
        return grantRepository.findById(sessionId)
                .orElseGet(() -> {
                    SessionAccessGrant grant = new SessionAccessGrant();
                    grant.setSessionId(sessionId);
                    return grant;
                });
    }

    private User requireAccount(String email, RoleName requiredRole) {
        if (email == null || email.isBlank()) {
            throw forbidden();
        }

        User account = userRepository.findByEmailWithRole(email)
                .orElseThrow(this::forbidden);

        if (!"ACTIVE".equalsIgnoreCase(account.getStatus())
                || account.getId() == null
                || account.getRole() == null
                || account.getRole().getName() != requiredRole) {
            throw forbidden();
        }

        return account;
    }

    private ResponseStatusException forbidden() {
        return new ResponseStatusException(
                HttpStatus.FORBIDDEN,
                "You do not have permission to verify this access"
        );
    }
}