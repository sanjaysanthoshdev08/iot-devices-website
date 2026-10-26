package com.hykon.iot_backend.service;

import com.hykon.iot_backend.dto.RegisterRequest;
import com.hykon.iot_backend.dto.RegisterResponse;
import com.hykon.iot_backend.entity.Role;
import com.hykon.iot_backend.entity.RoleName;
import com.hykon.iot_backend.entity.User;
import com.hykon.iot_backend.exception.DuplicateResourceException;
import com.hykon.iot_backend.repository.RoleRepository;
import com.hykon.iot_backend.repository.UserRepository;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
public class DistributorAdminService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    public DistributorAdminService(
            UserRepository userRepository,
            RoleRepository roleRepository,
            PasswordEncoder passwordEncoder
    ) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public RegisterResponse create(
            RegisterRequest request,
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

        if (!request.getPassword().equals(request.getConfirmPassword())) {
            throw new IllegalArgumentException("Passwords do not match");
        }

        String email = request.getEmail().trim().toLowerCase(Locale.ROOT);

        if (userRepository.existsByEmail(email)) {
            throw new DuplicateResourceException("Email is already registered");
        }

        Role distributorAdminRole =
                roleRepository.findByName(RoleName.DISTRIBUTOR_ADMIN)
                        .orElseThrow(() ->
                                new IllegalStateException(
                                        "DISTRIBUTOR_ADMIN role is not configured"
                                ));

        User user = new User();
        user.setName(request.getName().trim());
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setRole(distributorAdminRole);
        user.setStatus("ACTIVE");

        return new RegisterResponse(userRepository.save(user));
    }
}