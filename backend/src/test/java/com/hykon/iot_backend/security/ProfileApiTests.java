package com.hykon.iot_backend.security;

import com.hykon.iot_backend.config.SecurityConfig;
import com.hykon.iot_backend.controller.ProfileController;
import com.hykon.iot_backend.entity.Role;
import com.hykon.iot_backend.entity.RoleName;
import com.hykon.iot_backend.entity.User;
import com.hykon.iot_backend.exception.GlobalExceptionHandler;
import com.hykon.iot_backend.repository.UserRepository;
import com.hykon.iot_backend.service.JwtService;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = ProfileController.class)
@Import({
        SecurityConfig.class,
        GlobalExceptionHandler.class,
        SecurityAndValidationTests.NoDefaultUserConfiguration.class
})
class ProfileApiTests {

    private static final String ACCOUNT_EMAIL = "account@example.com";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserRepository userRepository;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private com.hykon.iot_backend.service.LoginSessionService loginSessionService;

    private void stubAccount(RoleName role) {
        User user = new User();
        user.setName("Current Account");
        user.setEmail(ACCOUNT_EMAIL);
        user.setPassword("must-never-appear-in-response");
        user.setStatus("ACTIVE");
        user.setRole(new Role(role));

        when(userRepository.findByEmailWithRole(ACCOUNT_EMAIL))
                .thenReturn(Optional.of(user));
    }

    private void assertOwnProfile(RoleName role) throws Exception {
        stubAccount(role);

        mockMvc.perform(get("/api/v1/profile"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.email").value(ACCOUNT_EMAIL))
                .andExpect(jsonPath("$.data.role").value(role.name()))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.password").doesNotExist())
                .andExpect(jsonPath("$.data.accessToken").doesNotExist())
                .andExpect(jsonPath("$.data.refreshToken").doesNotExist());

        verify(userRepository).findByEmailWithRole(ACCOUNT_EMAIL);
    }

    @Test
    void unauthenticatedProfileRequestIsDenied() throws Exception {
        mockMvc.perform(get("/api/v1/profile"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(userRepository);
    }

    @Test
    @WithMockUser(username = ACCOUNT_EMAIL, roles = "USER")
    void userCanReadOwnProfile() throws Exception {
        assertOwnProfile(RoleName.USER);
    }

    @Test
    @WithMockUser(username = ACCOUNT_EMAIL, roles = "DISTRIBUTOR_ADMIN")
    void distributorAdminCanReadOwnProfile() throws Exception {
        assertOwnProfile(RoleName.DISTRIBUTOR_ADMIN);
    }

    @Test
    @WithMockUser(username = ACCOUNT_EMAIL, roles = "HYKON_ADMIN")
    void hykonAdminCanReadOwnProfile() throws Exception {
        assertOwnProfile(RoleName.HYKON_ADMIN);
    }

    @Test
    @WithMockUser(username = ACCOUNT_EMAIL, roles = "USER")
    void suppliedEmailCannotSelectAnotherAccount() throws Exception {
        stubAccount(RoleName.USER);

        mockMvc.perform(get("/api/v1/profile")
                        .param("email", "another-account@example.com")
                        .param("id", "999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value(ACCOUNT_EMAIL));

        verify(userRepository).findByEmailWithRole(ACCOUNT_EMAIL);
        verifyNoMoreInteractions(userRepository);
    }
}