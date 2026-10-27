package com.hykon.iot_backend.security;

import com.hykon.iot_backend.config.SecurityConfig;
import com.hykon.iot_backend.controller.DevicePurchaseController;
import com.hykon.iot_backend.dto.DevicePurchaseResponse;
import com.hykon.iot_backend.dto.RecordDevicePurchaseRequest;
import com.hykon.iot_backend.exception.DuplicateResourceException;
import com.hykon.iot_backend.exception.GlobalExceptionHandler;
import com.hykon.iot_backend.repository.UserRepository;
import com.hykon.iot_backend.service.DevicePurchaseService;
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

@WebMvcTest(controllers = DevicePurchaseController.class)
@Import({
        SecurityConfig.class,
        GlobalExceptionHandler.class,
        SecurityAndValidationTests.NoDefaultUserConfiguration.class
})
class DevicePurchaseApiTests {

    private static final String ENDPOINT =
            "/api/v1/management/device-purchases";

    private static final String VALID_REQUEST = """
            {
              "deviceId": 1,
              "customerUserId": 30,
              "purchaseReference": "INV-001"
            }
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DevicePurchaseService purchaseService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private com.hykon.iot_backend.service.LoginSessionService loginSessionService;

    @MockitoBean
    private UserRepository userRepository;

    @Test
    void unauthenticatedRecordingIsDenied() throws Exception {
        mockMvc.perform(post(ENDPOINT)
                        .contentType(APPLICATION_JSON)
                        .content(VALID_REQUEST))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(purchaseService);
    }

    @Test
    @WithMockUser(roles = "USER")
    void customerCannotGrantPurchaseEligibility() throws Exception {
        mockMvc.perform(post(ENDPOINT)
                        .contentType(APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(purchaseService);
    }

    private void assertSuccessfulRecording(Long creatorId) throws Exception {
        DevicePurchaseResponse response = new DevicePurchaseResponse(
                1L, 1L, "HALO1200-1", 30L, creatorId,
                "INV-001", "ELIGIBLE", null, null
        );

        when(purchaseService.record(
                any(RecordDevicePurchaseRequest.class),
                eq("creator@example.com")
        )).thenReturn(response);

        mockMvc.perform(post(ENDPOINT)
                        .contentType(APPLICATION_JSON)
                        .content(VALID_REQUEST))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.deviceId").value(1))
                .andExpect(jsonPath("$.data.customerUserId").value(30))
                .andExpect(jsonPath("$.data.status").value("ELIGIBLE"))
                .andExpect(jsonPath("$.data.password").doesNotExist())
                .andExpect(jsonPath("$.data.accessToken").doesNotExist());

        verify(purchaseService).record(
                any(RecordDevicePurchaseRequest.class),
                eq("creator@example.com")
        );
    }

    @Test
    @WithMockUser(
            username = "creator@example.com",
            roles = "HYKON_ADMIN"
    )
    void hykonAdminCanSubmitPurchaseRecord() throws Exception {
        assertSuccessfulRecording(1L);
    }

    @Test
    @WithMockUser(
            username = "creator@example.com",
            roles = "DISTRIBUTOR_ADMIN"
    )
    void distributorAdminCanSubmitPurchaseRecord() throws Exception {
        assertSuccessfulRecording(22L);
    }

    @Test
    @WithMockUser(roles = "HYKON_ADMIN")
    void invalidPurchaseDetailsAreRejected() throws Exception {
        mockMvc.perform(post(ENDPOINT)
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "deviceId": 0,
                                  "customerUserId": -1,
                                  "purchaseReference": ""
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.deviceId").exists())
                .andExpect(jsonPath("$.errors.customerUserId").exists())
                .andExpect(jsonPath("$.errors.purchaseReference").exists());

        verifyNoInteractions(purchaseService);
    }

    @Test
    @WithMockUser(
            username = "creator@example.com",
            roles = "HYKON_ADMIN"
    )
    void existingPurchaseReturnsConflict() throws Exception {
        when(purchaseService.record(
                any(RecordDevicePurchaseRequest.class),
                eq("creator@example.com")
        )).thenThrow(new DuplicateResourceException(
                "A purchase record already exists for this device"
        ));

        mockMvc.perform(post(ENDPOINT)
                        .contentType(APPLICATION_JSON)
                        .content(VALID_REQUEST))
                .andExpect(status().isConflict());
    }
}