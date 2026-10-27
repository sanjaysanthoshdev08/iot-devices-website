package com.hykon.iot_backend.security;

import com.hykon.iot_backend.config.SecurityConfig;
import com.hykon.iot_backend.controller.DistributorController;
import com.hykon.iot_backend.dto.CreateDistributorRequest;
import com.hykon.iot_backend.dto.DistributorResponse;
import com.hykon.iot_backend.exception.GlobalExceptionHandler;
import com.hykon.iot_backend.repository.UserRepository;
import com.hykon.iot_backend.service.DistributorService;
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

@WebMvcTest(controllers = DistributorController.class)
@Import({
        SecurityConfig.class,
        GlobalExceptionHandler.class,
        SecurityAndValidationTests.NoDefaultUserConfiguration.class
})
class DistributorApiTests {

    private static final String ENDPOINT = "/api/v1/admin/distributors";

    private static final String VALID_REQUEST = """
            {
              "distributorCode": "DIST-A",
              "name": "Distributor A",
              "administratorUserId": 20,
              "branchCode": "MAIN",
              "branchName": "Main Branch",
              "location": "Kochi"
            }
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DistributorService distributorService;

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

        verifyNoInteractions(distributorService);
    }

    @Test
    @WithMockUser(roles = "USER")
    void customerCannotCreateDistributor() throws Exception {
        mockMvc.perform(post(ENDPOINT)
                        .contentType(APPLICATION_JSON)
                        .content(VALID_REQUEST))
                .andExpect(status().isForbidden());

        verifyNoInteractions(distributorService);
    }

    @Test
    @WithMockUser(roles = "DISTRIBUTOR_ADMIN")
    void distributorAdminCannotCreateDistributor() throws Exception {
        mockMvc.perform(post(ENDPOINT)
                        .contentType(APPLICATION_JSON)
                        .content(VALID_REQUEST))
                .andExpect(status().isForbidden());

        verifyNoInteractions(distributorService);
    }

    @Test
    @WithMockUser(
            username = "creator@example.com",
            roles = "HYKON_ADMIN"
    )
    void hykonAdminCanCreateDistributorAndFirstBranch() throws Exception {
        DistributorResponse response = new DistributorResponse(
                1L,
                "DIST-A",
                "Distributor A",
                20L,
                "ACTIVE",
                10L,
                "MAIN",
                "Main Branch",
                "Kochi",
                "ACTIVE"
        );

        when(distributorService.create(
                any(CreateDistributorRequest.class),
                eq("creator@example.com")
        )).thenReturn(response);

        mockMvc.perform(post(ENDPOINT)
                        .contentType(APPLICATION_JSON)
                        .content(VALID_REQUEST))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.distributorCode").value("DIST-A"))
                .andExpect(jsonPath("$.data.administratorUserId").value(20))
                .andExpect(jsonPath("$.data.branchCode").value("MAIN"))
                .andExpect(jsonPath("$.data.location").value("Kochi"))
                .andExpect(jsonPath("$.data.password").doesNotExist())
                .andExpect(jsonPath("$.data.accessToken").doesNotExist());

        verify(distributorService).create(
                any(CreateDistributorRequest.class),
                eq("creator@example.com")
        );
    }

    @Test
    @WithMockUser(roles = "HYKON_ADMIN")
    void missingRequiredDetailsAreRejected() throws Exception {
        mockMvc.perform(post(ENDPOINT)
                        .contentType(APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.distributorCode").exists())
                .andExpect(jsonPath("$.errors.name").exists())
                .andExpect(jsonPath("$.errors.administratorUserId").exists())
                .andExpect(jsonPath("$.errors.branchCode").exists())
                .andExpect(jsonPath("$.errors.branchName").exists())
                .andExpect(jsonPath("$.errors.location").exists());

        verifyNoInteractions(distributorService);
    }
}