package com.hykon.iot_backend.security;

import com.hykon.iot_backend.config.SecurityConfig;
import com.hykon.iot_backend.controller.DistributorAdminController;
import com.hykon.iot_backend.dto.RegisterRequest;
import com.hykon.iot_backend.dto.RegisterResponse;
import com.hykon.iot_backend.entity.Role;
import com.hykon.iot_backend.entity.RoleName;
import com.hykon.iot_backend.entity.User;
import com.hykon.iot_backend.exception.GlobalExceptionHandler;
import com.hykon.iot_backend.repository.UserRepository;
import com.hykon.iot_backend.service.DistributorAdminService;
import com.hykon.iot_backend.service.JwtService;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = DistributorAdminController.class)
@Import({
        SecurityConfig.class,
        GlobalExceptionHandler.class,
        SecurityAndValidationTests.NoDefaultUserConfiguration.class
})
class DistributorAdminApiTests {

    private static final String ENDPOINT =
            "/api/v1/admin/distributor-admins";

    private static final String VALID_REQUEST = """
            {
              "name": "Distributor Manager",
              "email": "manager@example.com",
              "password": "TestOnly!Pass123",
              "confirmPassword": "TestOnly!Pass123"
            }
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DistributorAdminService distributorAdminService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private com.hykon.iot_backend.service.LoginSessionService loginSessionService;

    @MockitoBean
    private UserRepository userRepository;

    @Test
    void unauthenticatedCreationIsDenied() throws Exception {
        mockMvc.perform(post(ENDPOINT)
                        .contentType(APPLICATION_JSON)
                        .content(VALID_REQUEST))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(distributorAdminService);
    }

    @Test
    @WithMockUser(roles = "USER")
    void userCannotCreateDistributorAdmin() throws Exception {
        mockMvc.perform(post(ENDPOINT)
                        .contentType(APPLICATION_JSON)
                        .content(VALID_REQUEST))
                .andExpect(status().isForbidden());

        verifyNoInteractions(distributorAdminService);
    }

    @Test
    @WithMockUser(roles = "DISTRIBUTOR_ADMIN")
    void distributorAdminCannotCreateAnotherAdmin() throws Exception {
        mockMvc.perform(post(ENDPOINT)
                        .contentType(APPLICATION_JSON)
                        .content(VALID_REQUEST))
                .andExpect(status().isForbidden());

        verifyNoInteractions(distributorAdminService);
    }

    @Test
    @WithMockUser(
            username = "creator@example.com",
            roles = "HYKON_ADMIN"
    )
    void hykonAdminCanCreateDistributorAdmin() throws Exception {
        User createdUser = new User();
        createdUser.setName("Distributor Manager");
        createdUser.setEmail("manager@example.com");
        createdUser.setPassword("must-never-appear-in-response");
        createdUser.setRole(new Role(RoleName.DISTRIBUTOR_ADMIN));
        createdUser.setStatus("ACTIVE");

        when(distributorAdminService.create(
                any(RegisterRequest.class),
                eq("creator@example.com")
        )).thenReturn(new RegisterResponse(createdUser));

        mockMvc.perform(post(ENDPOINT)
                        .contentType(APPLICATION_JSON)
                        .content(VALID_REQUEST))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.role")
                        .value("DISTRIBUTOR_ADMIN"))
                .andExpect(jsonPath("$.data.password").doesNotExist())
                .andExpect(jsonPath("$.data.accessToken").doesNotExist())
                .andExpect(jsonPath("$.data.refreshToken").doesNotExist());

        verify(distributorAdminService).create(
                any(RegisterRequest.class),
                eq("creator@example.com")
        );
    }

    @Test
    @WithMockUser(roles = "HYKON_ADMIN")
    void invalidCreationPayloadIsRejected() throws Exception {
        mockMvc.perform(post(ENDPOINT)
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "",
                                  "email": "invalid-email",
                                  "password": "short",
                                  "confirmPassword": ""
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.name").exists())
                .andExpect(jsonPath("$.errors.email").exists())
                .andExpect(jsonPath("$.errors.password").exists());

        verifyNoInteractions(distributorAdminService);
    }
}