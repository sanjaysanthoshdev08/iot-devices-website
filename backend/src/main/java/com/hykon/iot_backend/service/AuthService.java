package com.hykon.iot_backend.service;

import com.hykon.iot_backend.dto.RegisterRequest;
import com.hykon.iot_backend.entity.Role;
import com.hykon.iot_backend.entity.RoleName;
import com.hykon.iot_backend.entity.User;
import com.hykon.iot_backend.repository.RoleRepository;
import com.hykon.iot_backend.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;

    public AuthService(
            UserRepository userRepository,
            RoleRepository roleRepository
    ) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
    }

    @Transactional
    public User register(RegisterRequest request) {

        String name = request.getName().trim();
        String email = request.getEmail().trim().toLowerCase();

        if (!request.getPassword().equals(request.getConfirmPassword())) {
            throw new IllegalArgumentException("Passwords do not match");
        }

        if (userRepository.existsByEmail(email)) {
            throw new IllegalArgumentException("Email is already registered");
        }

        Role userRole = roleRepository.findByName(RoleName.USER)
                .orElseThrow(() ->
                        new IllegalStateException("USER role is not configured"));

        User user = new User();
        user.setName(name);
        user.setEmail(email);

        // Password hashing will be added in Step 17.
        user.setPassword(request.getPassword());

        user.setRole(userRole);
        user.setStatus("ACTIVE");

        return userRepository.save(user);
    }
}