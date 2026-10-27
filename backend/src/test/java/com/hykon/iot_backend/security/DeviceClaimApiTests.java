package com.hykon.iot_backend.security;

import com.hykon.iot_backend.config.SecurityConfig;
import com.hykon.iot_backend.controller.DeviceClaimController;
import com.hykon.iot_backend.dto.ClaimDeviceRequest;
import com.hykon.iot_backend.dto.DeviceResponse;
import com.hykon.iot_backend.exception.GlobalExceptionHandler;
import com.hykon.iot_backend.repository.UserRepository;
import com.hykon.iot_backend.service.SessionAccessVerificationService;
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

@WebMvcTest(controllers = DeviceClaimController.class)
@Import({
        SecurityConfig.class,
        GlobalExceptionHandler.class,
        SecurityAndValidationTests.NoDefaultUserConfiguration.class
})
class DeviceClaimApiTests {

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
            username = "customer@example.com",
            roles = "USER"
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

        verifyNoInteractions(claimService);
    }

    private static final String ENDPOINT = "/api/v1/user/devices/claim";

    private static final String VALID_REQUEST = """
            {
              "deviceCode": "HALO1200-1",
              "serialNumber": "SN-001"
            }
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SessionAccessVerificationService claimService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private com.hykon.iot_backend.service.LoginSessionService loginSessionService;

    @MockitoBean
    private UserRepository userRepository;

    @Test
    void unauthenticatedClaimIsDenied() throws Exception {
        mockMvc.perform(post(ENDPOINT)
                        .contentType(APPLICATION_JSON)
                        .content(VALID_REQUEST))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(claimService);
    }

    private void assertAdminDenied() throws Exception {
        mockMvc.perform(post(ENDPOINT)
                        .contentType(APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(claimService);
    }

    @Test
    @WithMockUser(roles = "HYKON_ADMIN")
    void hykonAdminCannotUseCustomerClaimEndpoint() throws Exception {
        assertAdminDenied();
    }

    @Test
    @WithMockUser(roles = "DISTRIBUTOR_ADMIN")
    void distributorAdminCannotUseCustomerClaimEndpoint() throws Exception {
        assertAdminDenied();
    }

    @Test
    @WithMockUser(
            username = "customer@example.com",
            roles = "USER"
    )
    void customerClaimUsesAuthenticatedIdentity() throws Exception {
        DeviceResponse response = new DeviceResponse(
                1L, "HALO1200-1", "SN-001", "HALO 1200",
                null, 1L, 1L, 30L, "ACTIVE", null
        );

        when(claimService.verifyDevice(
                any(ClaimDeviceRequest.class),
                eq("customer@example.com"), eq(SESSION_ID)
        )).thenReturn(response);

        mockMvc.perform(post(ENDPOINT)
                        .contentType(APPLICATION_JSON)
                        .content(VALID_REQUEST))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.ownerUserId").value(30))
                .andExpect(jsonPath("$.data.deviceCode").value("HALO1200-1"))
                .andExpect(jsonPath("$.data.password").doesNotExist())
                .andExpect(jsonPath("$.data.accessToken").doesNotExist());

        verify(claimService).verifyDevice(
                any(ClaimDeviceRequest.class),
                eq("customer@example.com"), eq(SESSION_ID)
        );
    }

    @Test
    @WithMockUser(roles = "USER")
    void missingDeviceCredentialsAreRejected() throws Exception {
        mockMvc.perform(post(ENDPOINT)
                        .contentType(APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.deviceCode").exists())
                .andExpect(jsonPath("$.errors.serialNumber").exists());

        verifyNoInteractions(claimService);
    }

    @Test
    @WithMockUser(
            username = "customer@example.com",
            roles = "USER"
    )
    void failedVerificationReturnsGenericBadRequest() throws Exception {
        when(claimService.verifyDevice(
                any(ClaimDeviceRequest.class),
                eq("customer@example.com"), eq(SESSION_ID)
        )).thenThrow(new IllegalArgumentException(
                "Device verification failed"
        ));

        mockMvc.perform(post(ENDPOINT)
                        .contentType(APPLICATION_JSON)
                        .content(VALID_REQUEST))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message")
                        .value("Device verification failed"));
    }
}