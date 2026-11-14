package com.hykon.iot_backend.service;

import com.hykon.iot_backend.dto.CreateDistributorWithLoginRequest;
import com.hykon.iot_backend.dto.DistributorResponse;
import com.hykon.iot_backend.entity.Distributor;
import com.hykon.iot_backend.entity.Role;
import com.hykon.iot_backend.entity.RoleName;
import com.hykon.iot_backend.entity.User;
import com.hykon.iot_backend.exception.DuplicateResourceException;
import com.hykon.iot_backend.repository.DistributorRepository;
import com.hykon.iot_backend.repository.RoleRepository;
import com.hykon.iot_backend.repository.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

/**
 * The overseeing Distributor Admin (or HYKON Admin) creates a distributor organization and its
 * own login in one transaction. Distributor code and login email must both be unique.
 */
@Service
public class DistributorProvisioningService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final DistributorRepository distributorRepository;
    private final PasswordEncoder passwordEncoder;

    public DistributorProvisioningService(
            UserRepository userRepository,
            RoleRepository roleRepository,
            DistributorRepository distributorRepository,
            PasswordEncoder passwordEncoder
    ) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.distributorRepository = distributorRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public DistributorResponse create(CreateDistributorWithLoginRequest request, String actorEmail) {
        User actor = actorEmail == null || actorEmail.isBlank() ? null
                : userRepository.findByEmailWithRole(actorEmail).orElse(null);
        if (actor == null
                || !"ACTIVE".equalsIgnoreCase(actor.getStatus())
                || actor.getRole() == null
                || !(actor.getRole().getName() == RoleName.HYKON_ADMIN || actor.seesAllDistributors())) {
            throw new AccessDeniedException("Distributor administrator access is required");
        }

        if (!request.getPassword().equals(request.getConfirmPassword())) {
            throw new IllegalArgumentException("Passwords do not match");
        }

        String code = request.getDistributorCode().trim().toUpperCase(Locale.ROOT);
        String email = request.getEmail().trim().toLowerCase(Locale.ROOT);

        if (distributorRepository.findByCode(code).isPresent()) {
            throw new DuplicateResourceException("Distributor code is already registered");
        }
        if (userRepository.existsByEmail(email)) {
            throw new DuplicateResourceException("Email is already registered");
        }

        Role role = roleRepository.findByName(RoleName.DISTRIBUTOR_ADMIN)
                .orElseThrow(() -> new IllegalStateException("DISTRIBUTOR_ADMIN role is not configured"));

        User login = new User();
        login.setName(request.getName().trim());
        login.setEmail(email);
        login.setPassword(passwordEncoder.encode(request.getPassword()));
        login.setRole(role);
        login.setStatus("ACTIVE");
        login.setAllDistributors(false);

        try {
            User savedLogin = userRepository.saveAndFlush(login);

            Distributor distributor = new Distributor();
            distributor.setDistributorCode(code);
            distributor.setName(request.getName().trim());
            distributor.setAdministrator(savedLogin);
            distributor.setCreatedBy(actor);
            distributor.setStatus("ACTIVE");
            distributor.setState(request.getState().trim());
            distributor.setDistrict(request.getDistrict().trim());

            return DistributorResponse.from(distributorRepository.saveAndFlush(distributor), null);
        } catch (DataIntegrityViolationException exception) {
            throw new DuplicateResourceException(
                    "Distributor details conflict with an existing database record");
        }
    }
}
