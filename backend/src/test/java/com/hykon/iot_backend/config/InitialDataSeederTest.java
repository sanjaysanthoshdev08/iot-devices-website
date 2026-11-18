package com.hykon.iot_backend.config;

import com.hykon.iot_backend.entity.Role;
import com.hykon.iot_backend.entity.RoleName;
import com.hykon.iot_backend.entity.User;
import com.hykon.iot_backend.repository.RoleRepository;
import com.hykon.iot_backend.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InitialDataSeederTest {

    private final RoleRepository roles = mock(RoleRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final PasswordEncoder encoder = mock(PasswordEncoder.class);

    @Test
    void createsOnlyMissingRolesAndNoUsersWhenNothingIsConfigured() {
        when(roles.findByName(any())).thenReturn(Optional.empty());
        new InitialDataSeeder(roles, users, encoder, "", "", "", "").run();

        verify(roles, org.mockito.Mockito.times(RoleName.values().length)).save(any(Role.class));
        verify(users, never()).save(any(User.class));
    }

    @Test
    void doesNotTouchExistingRolesOrAccounts() {
        when(roles.findByName(any())).thenReturn(Optional.of(new Role(RoleName.USER)));
        when(users.existsByEmail("admin@x.com")).thenReturn(true);
        new InitialDataSeeder(roles, users, encoder, "admin@x.com", "Secret@123", "", "").run();

        verify(roles, never()).save(any(Role.class));
        verify(users, never()).save(any(User.class));
    }

    @Test
    void createsHykonAdminAndOverseerWhenConfiguredAndMissing() {
        when(roles.findByName(any())).thenAnswer(inv -> Optional.of(new Role(inv.getArgument(0))));
        when(users.existsByEmail(any())).thenReturn(false);
        when(encoder.encode("Secret@123")).thenReturn("HASH");
        new InitialDataSeeder(roles, users, encoder, "admin@x.com", "Secret@123", "over@x.com", "Secret@123").run();

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(users, org.mockito.Mockito.times(2)).save(saved.capture());
        User admin = saved.getAllValues().get(0);
        User overseer = saved.getAllValues().get(1);
        assertEquals(RoleName.HYKON_ADMIN, admin.getRole().getName());
        assertEquals("HASH", admin.getPassword());
        assertEquals("ACTIVE", admin.getStatus());
        assertEquals(RoleName.DISTRIBUTOR_ADMIN, overseer.getRole().getName());
        assertTrue(overseer.isAllDistributors());
    }
}
