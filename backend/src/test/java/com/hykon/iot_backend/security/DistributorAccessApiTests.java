package com.hykon.iot_backend.security;

import com.hykon.iot_backend.config.SecurityConfig;
import com.hykon.iot_backend.controller.DistributorAccessController;
import com.hykon.iot_backend.dto.DistributorAccessRequest;
import com.hykon.iot_backend.dto.DistributorAccessResponse;
import com.hykon.iot_backend.exception.GlobalExceptionHandler;
import com.hykon.iot_backend.repository.UserRepository;
import com.hykon.iot_backend.service.AccessVerificationCoordinator;
import com.hykon.iot_backend.service.JwtService;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = DistributorAccessController.class)
@Import({
        SecurityConfig.class,
        GlobalExceptionHandler.class,
        SecurityAndValidationTests.NoDefaultUserConfiguration.class
})
class DistributorAccessApiTests {

    @Test
    @WithMockUser(
            username = "distributor@example.com",
            roles = "DISTRIBUTOR_ADMIN"
    )
    void lockoutReturnsTooManyRequests() throws Exception {
        when(accessService.verifyDistributor(
                any(DistributorAccessRequest.class),
                eq(EMAIL),
                eq(SESSION_ID)
        )).thenThrow(new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.TOO_MANY_REQUESTS,
                "Too many verification attempts. Try again later."
        ));

        mockMvc.perform(post(ENDPOINT)
                        .contentType(APPLICATION_JSON)
                        .content(VALID_REQUEST))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.message").value(
                        "Too many verification attempts. Try again later."
                ));
    }

    private static final java.util.UUID SESSION_ID =
            java.util.UUID.randomUUID();

    @org.junit.jupiter.api.BeforeEach
    void attachAuthenticatedTestSession() {
        var authentication = org.springframework.security.core.context
                .SecurityContextHolder.getContext().getAuthentication();

        if (authentication instanceof
                org.springframework.security.authentication.UsernamePasswordAuthenticationToken token) {
            token.setDetails(SESSION_ID);
        }
    }

    @Test
    @WithMockUser(
            username = "distributor@example.com",
            roles = "DISTRIBUTOR_ADMIN"
    )
    void missingSessionIdentityIsRejectedBeforeVerification() throws Exception {
        var authentication = org.springframework.security.core.context
                .SecurityContextHolder.getContext().getAuthentication();

        ((org.springframework.security.authentication.UsernamePasswordAuthenticationToken)
                authentication).setDetails(null);

        mockMvc.perform(post(ENDPOINT)
                        .contentType(APPLICATION_JSON)
                        .content(VALID_REQUEST))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(accessService);
    }

    private static final String ENDPOINT =
            "/api/v1/distributor/access/verify";

    private static final String EMAIL = "distributor@example.com";

    private static final String VALID_REQUEST = """
            {
              "distributorCode": "DIST-A",
              "branchCode": "MAIN",
              "location": "EKM"
            }
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AccessVerificationCoordinator accessService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private com.hykon.iot_backend.service.LoginSessionService loginSessionService;

    @MockitoBean
    private UserRepository userRepository;

    @Test
    void anonymousRequestIsRejected() throws Exception {
        mockMvc.perform(post(ENDPOINT)
                        .contentType(APPLICATION_JSON)
                        .content(VALID_REQUEST))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(accessService);
    }

    private void assertRoleDenied() throws Exception {
        mockMvc.perform(post(ENDPOINT)
                        .contentType(APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(accessService);
    }

    @Test
    @WithMockUser(roles = "USER")
    void customerCannotUseDistributorVerification() throws Exception {
        assertRoleDenied();
    }

    @Test
    @WithMockUser(roles = "HYKON_ADMIN")
    void companyAdminCannotUseDistributorVerification() throws Exception {
        assertRoleDenied();
    }

    @Test
    @WithMockUser(
            username = EMAIL,
            roles = "DISTRIBUTOR_ADMIN"
    )
    void distributorVerificationUsesAuthenticatedIdentity() throws Exception {
        DistributorAccessResponse response =
                new DistributorAccessResponse(
                        1L, "DIST-A", "DEV",
                        1L, "MAIN", "ERNAKULAM", "EKM"
                );

        when(accessService.verifyDistributor(
                any(DistributorAccessRequest.class), eq(EMAIL), eq(SESSION_ID)
        )).thenReturn(response);

        mockMvc.perform(post(ENDPOINT)
                        .contentType(APPLICATION_JSON)
                        .content(VALID_REQUEST))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.distributorId").value(1))
                .andExpect(jsonPath("$.data.distributorCode").value("DIST-A"))
                .andExpect(jsonPath("$.data.branchId").value(1))
                .andExpect(jsonPath("$.data.branchCode").value("MAIN"))
                .andExpect(jsonPath("$.data.location").value("EKM"))
                .andExpect(jsonPath("$.data.password").doesNotExist())
                .andExpect(jsonPath("$.data.accessToken").doesNotExist());

        ArgumentCaptor<DistributorAccessRequest> captured =
                ArgumentCaptor.forClass(DistributorAccessRequest.class);

        verify(accessService).verifyDistributor(captured.capture(), eq(EMAIL), eq(SESSION_ID));

        assertEquals("DIST-A", captured.getValue().getDistributorCode());
        assertEquals("MAIN", captured.getValue().getBranchCode());
        assertEquals("EKM", captured.getValue().getLocation());
    }

    @Test
    @WithMockUser(
            username = EMAIL,
            roles = "DISTRIBUTOR_ADMIN"
    )
    void missingFieldsAreRejectedBeforeServiceCall() throws Exception {
        mockMvc.perform(post(ENDPOINT)
                        .contentType(APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.distributorCode").exists())
                .andExpect(jsonPath("$.errors.branchCode").exists())
                .andExpect(jsonPath("$.errors.location").exists());

        verifyNoInteractions(accessService);
    }

    @Test
    @WithMockUser(
            username = EMAIL,
            roles = "DISTRIBUTOR_ADMIN"
    )
    void incorrectDetailsReturnGenericVerificationFailure() throws Exception {
        when(accessService.verifyDistributor(
                any(DistributorAccessRequest.class), eq(EMAIL), eq(SESSION_ID)
        )).thenThrow(new IllegalArgumentException(
                "Distributor verification failed"
        ));

        mockMvc.perform(post(ENDPOINT)
                        .contentType(APPLICATION_JSON)
                        .content(VALID_REQUEST))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message")
                        .value("Distributor verification failed"))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    @WithMockUser(
            username = EMAIL,
            roles = "DISTRIBUTOR_ADMIN"
    )
    void serviceDenialReturnsForbidden() throws Exception {
        when(accessService.verifyDistributor(
                any(DistributorAccessRequest.class), eq(EMAIL), eq(SESSION_ID)
        )).thenThrow(new ResponseStatusException(
                HttpStatus.FORBIDDEN,
                "DISTRIBUTOR_ADMIN authentication is required"
        ));

        mockMvc.perform(post(ENDPOINT)
                        .contentType(APPLICATION_JSON)
                        .content(VALID_REQUEST))
                .andExpect(status().isForbidden());
    }
}