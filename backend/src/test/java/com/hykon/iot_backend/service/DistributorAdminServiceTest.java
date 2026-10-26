package com.hykon.iot_backend.service;

import com.hykon.iot_backend.dto.RegisterRequest;
import com.hykon.iot_backend.dto.RegisterResponse;
import com.hykon.iot_backend.entity.Role;
import com.hykon.iot_backend.entity.RoleName;
import com.hykon.iot_backend.entity.User;
import com.hykon.iot_backend.exception.DuplicateResourceException;
import com.hykon.iot_backend.repository.RoleRepository;
import com.hykon.iot_backend.repository.UserRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DistributorAdminServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private RoleRepository roleRepository;

    private BCryptPasswordEncoder passwordEncoder;
    private DistributorAdminService service;

    @BeforeEach
    void setUp() {
        passwordEncoder = new BCryptPasswordEncoder();
        service = new DistributorAdminService(
                userRepository, roleRepository, passwordEncoder
        );
    }

    private RegisterRequest request() {
        RegisterRequest request = new RegisterRequest();
        request.setName("Distributor Manager");
        request.setEmail("Manager@Example.com");
        request.setPassword("TestOnly!Pass123");
        request.setConfirmPassword("TestOnly!Pass123");
        return request;
    }

    private User creator(RoleName role, String status) {
        User user = new User();
        user.setEmail("creator@example.com");
        user.setRole(new Role(role));
        user.setStatus(status);
        return user;
    }

    private void allowHykonCreator() {
        when(userRepository.findByEmailWithRole("creator@example.com"))
                .thenReturn(Optional.of(
                        creator(RoleName.HYKON_ADMIN, "ACTIVE")
                ));
    }

    @Test
    void hykonAdminCreatesDistributorAdminWithHashedPassword() {
        allowHykonCreator();

        when(roleRepository.findByName(RoleName.DISTRIBUTOR_ADMIN))
                .thenReturn(Optional.of(
                        new Role(RoleName.DISTRIBUTOR_ADMIN)
                ));

        when(userRepository.save(any(User.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        RegisterResponse response =
                service.create(request(), "creator@example.com");

        ArgumentCaptor<User> savedUser =
                ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(savedUser.capture());

        User user = savedUser.getValue();
        assertEquals("manager@example.com", user.getEmail());
        assertEquals(RoleName.DISTRIBUTOR_ADMIN, user.getRole().getName());
        assertEquals("ACTIVE", user.getStatus());
        assertNotEquals("TestOnly!Pass123", user.getPassword());
        assertTrue(passwordEncoder.matches(
                "TestOnly!Pass123", user.getPassword()
        ));
        assertEquals("DISTRIBUTOR_ADMIN", response.getRole());
    }

    @Test
    void userAndDistributorAdminCannotCreateAdminAccounts() {
        for (RoleName role : new RoleName[] {
                RoleName.USER, RoleName.DISTRIBUTOR_ADMIN
        }) {
            when(userRepository.findByEmailWithRole("creator@example.com"))
                    .thenReturn(Optional.of(creator(role, "ACTIVE")));

            assertThrows(AccessDeniedException.class,
                    () -> service.create(request(), "creator@example.com"));
        }

        verify(userRepository, never()).save(any(User.class));
        verifyNoInteractions(roleRepository);
    }

    @Test
    void inactiveHykonAdminCannotCreateAccounts() {
        when(userRepository.findByEmailWithRole("creator@example.com"))
                .thenReturn(Optional.of(
                        creator(RoleName.HYKON_ADMIN, "INACTIVE")
                ));

        assertThrows(AccessDeniedException.class,
                () -> service.create(request(), "creator@example.com"));

        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void missingCreatorCannotCreateAccounts() {
        assertThrows(AccessDeniedException.class,
                () -> service.create(request(), null));

        when(userRepository.findByEmailWithRole("unknown@example.com"))
                .thenReturn(Optional.empty());

        assertThrows(AccessDeniedException.class,
                () -> service.create(request(), "unknown@example.com"));

        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void duplicateEmailIsRejected() {
        allowHykonCreator();

        when(userRepository.existsByEmail("manager@example.com"))
                .thenReturn(true);

        assertThrows(DuplicateResourceException.class,
                () -> service.create(request(), "creator@example.com"));

        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void mismatchedPasswordsAreRejected() {
        allowHykonCreator();
        RegisterRequest request = request();
        request.setConfirmPassword("Different!Pass123");

        assertThrows(IllegalArgumentException.class,
                () -> service.create(request, "creator@example.com"));

        verify(userRepository, never()).save(any(User.class));
    }
}