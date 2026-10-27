package com.hykon.iot_backend.security;

import com.hykon.iot_backend.config.SecurityConfig;
import com.hykon.iot_backend.controller.DeviceManagementController;
import com.hykon.iot_backend.dto.DeviceResponse;
import com.hykon.iot_backend.dto.RegisterDeviceRequest;
import com.hykon.iot_backend.exception.DuplicateResourceException;
import com.hykon.iot_backend.exception.GlobalExceptionHandler;
import com.hykon.iot_backend.repository.UserRepository;
import com.hykon.iot_backend.service.DeviceRegistrationService;
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

@WebMvcTest(controllers = DeviceManagementController.class)
@Import({
        SecurityConfig.class,
        GlobalExceptionHandler.class,
        SecurityAndValidationTests.NoDefaultUserConfiguration.class
})
class DeviceRegistrationApiTests {

    private static final String ENDPOINT = "/api/v1/management/devices";

    private static final String VALID_REQUEST = """
            {
              "deviceCode": "HALO1200-1",
              "serialNumber": "SN-001",
              "systemName": "HALO 1200",
              "branchId": 1
            }
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DeviceRegistrationService registrationService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private com.hykon.iot_backend.service.LoginSessionService loginSessionService;

    @MockitoBean
    private UserRepository userRepository;

    @Test
    void unauthenticatedRegistrationIsDenied() throws Exception {
        mockMvc.perform(post(ENDPOINT)
                        .contentType(APPLICATION_JSON)
                        .content(VALID_REQUEST))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(registrationService);
    }

    @Test
    @WithMockUser(roles = "USER")
    void customerIsDeniedBeforePayloadValidation() throws Exception {
        mockMvc.perform(post(ENDPOINT)
                        .contentType(APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(registrationService);
    }

    private void assertSuccessfulRegistration() throws Exception {
        DeviceResponse response = new DeviceResponse(
                1L, "HALO1200-1", "SN-001", "HALO 1200",
                null, 1L, 1L, null, "ACTIVE", null
        );

        when(registrationService.register(
                any(RegisterDeviceRequest.class),
                eq("creator@example.com")
        )).thenReturn(response);

        mockMvc.perform(post(ENDPOINT)
                        .contentType(APPLICATION_JSON)
                        .content(VALID_REQUEST))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.deviceCode").value("HALO1200-1"))
                .andExpect(jsonPath("$.data.branchId").value(1))
                .andExpect(jsonPath("$.data.password").doesNotExist())
                .andExpect(jsonPath("$.data.accessToken").doesNotExist());

        verify(registrationService).register(
                any(RegisterDeviceRequest.class),
                eq("creator@example.com")
        );
    }

    @Test
    @WithMockUser(
            username = "creator@example.com",
            roles = "HYKON_ADMIN"
    )
    void hykonAdminCanSubmitRegistration() throws Exception {
        assertSuccessfulRegistration();
    }

    @Test
    @WithMockUser(
            username = "creator@example.com",
            roles = "DISTRIBUTOR_ADMIN"
    )
    void distributorAdminCanSubmitRegistration() throws Exception {
        assertSuccessfulRegistration();
    }

    @Test
    @WithMockUser(roles = "HYKON_ADMIN")
    void missingDeviceDetailsAreRejected() throws Exception {
        mockMvc.perform(post(ENDPOINT)
                        .contentType(APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.deviceCode").exists())
                .andExpect(jsonPath("$.errors.serialNumber").exists())
                .andExpect(jsonPath("$.errors.systemName").exists());

        verifyNoInteractions(registrationService);
    }

    @Test
    @WithMockUser(
            username = "creator@example.com",
            roles = "HYKON_ADMIN"
    )
    void duplicateDeviceReturnsConflict() throws Exception {
        when(registrationService.register(
                any(RegisterDeviceRequest.class),
                eq("creator@example.com")
        )).thenThrow(new DuplicateResourceException(
                "Device ID or serial number is already registered"
        ));

        mockMvc.perform(post(ENDPOINT)
                        .contentType(APPLICATION_JSON)
                        .content(VALID_REQUEST))
                .andExpect(status().isConflict());
    }
}