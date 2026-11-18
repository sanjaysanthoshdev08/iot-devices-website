package com.hykon.iot_backend.config;

import com.hykon.iot_backend.entity.Role;
import com.hykon.iot_backend.entity.RoleName;
import com.hykon.iot_backend.entity.User;
import com.hykon.iot_backend.repository.RoleRepository;
import com.hykon.iot_backend.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Fills a brand-new (empty) database so the site is usable on first start, e.g. in Docker:
 * the four roles, and optionally the HYKON admin and the overseeing Distributor Admin.
 *
 * Additive and safe: it only inserts what is missing, never changes or deletes existing rows,
 * and creates a user only when its email AND password are supplied through environment variables.
 */
@Component
public class InitialDataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(InitialDataSeeder.class);

    private final RoleRepository roleRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    private final String hykonAdminEmail;
    private final String hykonAdminPassword;
    private final String overseerEmail;
    private final String overseerPassword;

    public InitialDataSeeder(
            RoleRepository roleRepository,
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            @Value("${HYKON_ADMIN_EMAIL:}") String hykonAdminEmail,
            @Value("${HYKON_ADMIN_PASSWORD:}") String hykonAdminPassword,
            @Value("${OVERSEER_EMAIL:}") String overseerEmail,
            @Value("${OVERSEER_PASSWORD:}") String overseerPassword
    ) {
        this.roleRepository = roleRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.hykonAdminEmail = hykonAdminEmail;
        this.hykonAdminPassword = hykonAdminPassword;
        this.overseerEmail = overseerEmail;
        this.overseerPassword = overseerPassword;
    }

    @Override
    @Transactional
    public void run(String... args) {
        for (RoleName name : RoleName.values()) {
            if (roleRepository.findByName(name).isEmpty()) {
                roleRepository.save(new Role(name));
                log.info("Seeded role {}", name);
            }
        }
        seedUser("HYKON Admin", hykonAdminEmail, hykonAdminPassword, RoleName.HYKON_ADMIN, false);
        seedUser("Distributor Admin", overseerEmail, overseerPassword, RoleName.DISTRIBUTOR_ADMIN, true);
    }

    private void seedUser(String name, String email, String password, RoleName roleName, boolean allDistributors) {
        if (email == null || email.isBlank() || password == null || password.isBlank()) {
            return;
        }
        String normalizedEmail = email.trim();
        if (userRepository.existsByEmail(normalizedEmail)) {
            return;
        }
        Role role = roleRepository.findByName(roleName).orElseThrow();
        User user = new User();
        user.setName(name);
        user.setEmail(normalizedEmail);
        user.setPassword(passwordEncoder.encode(password));
        user.setStatus("ACTIVE");
        user.setRole(role);
        user.setAllDistributors(allDistributors);
        userRepository.save(user);
        log.info("Seeded {} account {}", roleName, normalizedEmail);
    }
}
