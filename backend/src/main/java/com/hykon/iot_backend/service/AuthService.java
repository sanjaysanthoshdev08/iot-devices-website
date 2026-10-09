package com.hykon.iot_backend.service;

import com.hykon.iot_backend.dto.RegisterRequest;
import com.hykon.iot_backend.exception.InvalidTokenException;
import com.hykon.iot_backend.exception.DuplicateResourceException;
import com.hykon.iot_backend.entity.RefreshToken;
import com.hykon.iot_backend.entity.Role;
import com.hykon.iot_backend.entity.RoleName;
import com.hykon.iot_backend.entity.User;
import com.hykon.iot_backend.repository.RoleRepository;
import com.hykon.iot_backend.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenService refreshTokenService;

    public AuthService(
            UserRepository userRepository,
            RoleRepository roleRepository,
            PasswordEncoder passwordEncoder,
            RefreshTokenService refreshTokenService
    ) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.refreshTokenService = refreshTokenService;
    }

    @Transactional
    public User register(RegisterRequest request) {

        String name = request.getName().trim();
        String email = request.getEmail().trim().toLowerCase();

        if (!request.getPassword().equals(request.getConfirmPassword())) {
            throw new IllegalArgumentException("Passwords do not match");
        }

        if (userRepository.existsByEmail(email)) {
            throw new DuplicateResourceException("Email is already registered");
        }

        Role userRole = roleRepository.findByName(RoleName.USER)
                .orElseThrow(() ->
                        new IllegalStateException("USER role is not configured"));

        User user = new User();
        user.setName(name);
        user.setEmail(email);

        // Store only the BCrypt hash, never the raw password.
        user.setPassword(passwordEncoder.encode(request.getPassword()));

        user.setRole(userRole);
        user.setStatus("ACTIVE");

        return userRepository.save(user);
    }

    @Transactional
    public void logout(String refreshTokenValue) {

        RefreshToken refreshToken =
                refreshTokenService.findByToken(refreshTokenValue);

        if (!refreshTokenService.isTokenValid(refreshToken)) {
            throw new InvalidTokenException(
                    "Refresh token is expired or already revoked"
            );
        }

        refreshTokenService.revokeToken(refreshToken);
    }
}