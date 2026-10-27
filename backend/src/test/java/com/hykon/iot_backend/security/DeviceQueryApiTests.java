package com.hykon.iot_backend.security;

import com.hykon.iot_backend.config.SecurityConfig;
import com.hykon.iot_backend.controller.DeviceQueryController;
import com.hykon.iot_backend.dto.DeviceResponse;
import com.hykon.iot_backend.exception.GlobalExceptionHandler;
import com.hykon.iot_backend.repository.UserRepository;
import com.hykon.iot_backend.service.DeviceQueryService;
import com.hykon.iot_backend.service.JwtService;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = DeviceQueryController.class)
@Import({
        SecurityConfig.class,
        GlobalExceptionHandler.class,
        SecurityAndValidationTests.NoDefaultUserConfiguration.class
})
class DeviceQueryApiTests {

    private void assertUnverifiedSessionBlocked() throws Exception {
        doThrow(new ResponseStatusException(
                HttpStatus.FORBIDDEN,
                "Access verification is required"
        )).when(accessPolicy).requireVerified(
                any(org.springframework.security.core.Authentication.class)
        );

        mockMvc.perform(get(ENDPOINT))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message")
                        .value("Access verification is required"));

        mockMvc.perform(get(ENDPOINT + "/1"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message")
                        .value("Access verification is required"));

        verifyNoInteractions(queryService);
    }

    @Test
    @WithMockUser(
            username = "customer@example.com",
            roles = "USER"
    )
    void unverifiedCustomerCannotListOrReadDevices() throws Exception {
        assertUnverifiedSessionBlocked();
    }

    @Test
    @WithMockUser(
            username = "distributor@example.com",
            roles = "DISTRIBUTOR_ADMIN"
    )
    void unverifiedDistributorCannotListOrReadDevices() throws Exception {
        assertUnverifiedSessionBlocked();
    }

    private static final String ENDPOINT = "/api/v1/devices";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DeviceQueryService queryService;

    @MockitoBean
    private com.hykon.iot_backend.service.SessionAccessPolicyService accessPolicy;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private com.hykon.iot_backend.service.LoginSessionService loginSessionService;

    @MockitoBean
    private UserRepository userRepository;

    private DeviceResponse device() {
        return new DeviceResponse(
                1L, "HALO1200-1", "SN-001", "HALO 1200",
                null, 1L, 1L, 16L, "ACTIVE", null
        );
    }

    @Test
    void anonymousRequestsAreRejected() throws Exception {
        mockMvc.perform(get(ENDPOINT))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get(ENDPOINT + "/1"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(queryService);
    }

    @Test
    @WithMockUser(roles = "OTHER")
    void unsupportedRoleIsRejected() throws Exception {
        mockMvc.perform(get(ENDPOINT))
                .andExpect(status().isForbidden());

        mockMvc.perform(get(ENDPOINT + "/1"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(queryService);
    }

    private void assertDefaultList(String email) throws Exception {
        when(queryService.list(email, 0, 20))
                .thenReturn(new PageImpl<>(
                        List.of(device()), PageRequest.of(0, 20), 1
                ));

        mockMvc.perform(get(ENDPOINT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content[0].id").value(1))
                .andExpect(jsonPath("$.data.content[0].ownerUserId")
                        .value(16))
                .andExpect(jsonPath("$.data.content[0].password")
                        .doesNotExist())
                .andExpect(jsonPath("$.data.content[0].accessToken")
                        .doesNotExist());

        verify(queryService).list(email, 0, 20);
    }

    @Test
    @WithMockUser(username = "customer@example.com", roles = "USER")
    void customerListUsesAuthenticatedIdentity() throws Exception {
        assertDefaultList("customer@example.com");
    }

    @Test
    @WithMockUser(
            username = "distributor@example.com",
            roles = "DISTRIBUTOR_ADMIN"
    )
    void distributorListUsesAuthenticatedIdentity() throws Exception {
        assertDefaultList("distributor@example.com");
    }

    @Test
    @WithMockUser(username = "admin@example.com", roles = "HYKON_ADMIN")
    void companyAdminCanRequestList() throws Exception {
        assertDefaultList("admin@example.com");
    }

    @Test
    @WithMockUser(username = "customer@example.com", roles = "USER")
    void paginationIsPassedToService() throws Exception {
        when(queryService.list("customer@example.com", 2, 5))
                .thenReturn(new PageImpl<>(
                        List.of(), PageRequest.of(2, 5), 0
                ));

        mockMvc.perform(get(ENDPOINT)
                        .param("page", "2")
                        .param("size", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").isEmpty());

        verify(queryService).list("customer@example.com", 2, 5);
    }

    private void assertDetail(String email) throws Exception {
        when(queryService.get(1L, email)).thenReturn(device());

        mockMvc.perform(get(ENDPOINT + "/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.deviceCode")
                        .value("HALO1200-1"));

        verify(queryService).get(1L, email);
    }

    @Test
    @WithMockUser(username = "customer@example.com", roles = "USER")
    void customerDetailUsesAuthenticatedIdentity() throws Exception {
        assertDetail("customer@example.com");
    }

    @Test
    @WithMockUser(
            username = "distributor@example.com",
            roles = "DISTRIBUTOR_ADMIN"
    )
    void distributorDetailUsesAuthenticatedIdentity() throws Exception {
        assertDetail("distributor@example.com");
    }

    @Test
    @WithMockUser(username = "admin@example.com", roles = "HYKON_ADMIN")
    void companyAdminCanRequestDetail() throws Exception {
        assertDetail("admin@example.com");
    }

    @Test
    @WithMockUser(username = "customer@example.com", roles = "USER")
    void inaccessibleDeviceReturnsNotFound() throws Exception {
        when(queryService.get(99L, "customer@example.com"))
                .thenThrow(new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Device not found"
                ));

        mockMvc.perform(get(ENDPOINT + "/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Device not found"));
    }

    @Test
    @WithMockUser(
            username = "distributor@example.com",
            roles = "DISTRIBUTOR_ADMIN"
    )
    void serviceAccessDenialReturnsForbidden() throws Exception {
        when(queryService.list("distributor@example.com", 0, 20))
                .thenThrow(new ResponseStatusException(
                        HttpStatus.FORBIDDEN,
                        "You do not have permission to access this resource"
                ));

        mockMvc.perform(get(ENDPOINT))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "customer@example.com", roles = "USER")
    void invalidPaginationReturnsBadRequest() throws Exception {
        when(queryService.list("customer@example.com", -1, 20))
                .thenThrow(new IllegalArgumentException(
                        "Page must be at least 0 and size must be between 1 and 100"
                ));

        mockMvc.perform(get(ENDPOINT).param("page", "-1"))
                .andExpect(status().isBadRequest());
    }
}