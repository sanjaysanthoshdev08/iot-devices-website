package com.hykon.iot_backend.security;

import com.hykon.iot_backend.config.SecurityConfig;
import com.hykon.iot_backend.controller.AuthController;
import com.hykon.iot_backend.controller.RbacTestController;
import com.hykon.iot_backend.entity.Role;
import com.hykon.iot_backend.entity.RoleName;
import com.hykon.iot_backend.entity.User;
import com.hykon.iot_backend.exception.GlobalExceptionHandler;
import com.hykon.iot_backend.repository.UserRepository;
import com.hykon.iot_backend.service.AuthService;
import com.hykon.iot_backend.service.JwtService;
import com.hykon.iot_backend.service.LoginService;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {
        AuthController.class,
        RbacTestController.class
})
@Import({
        SecurityConfig.class,
        GlobalExceptionHandler.class
})
class SecurityAndValidationTests {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private LoginService loginService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private UserRepository userRepository;

    @Test
    void invalidRegistrationReturnsBadRequestWithFieldErrors()
            throws Exception {

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "name": " ",
                                  "email": "not-an-email",
                                  "password": "short",
                                  "confirmPassword": ""
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Validation failed"))
                .andExpect(jsonPath("$.errors.name").exists())
                .andExpect(jsonPath("$.errors.email").exists())
                .andExpect(jsonPath("$.errors.password").exists())
                .andExpect(jsonPath("$.errors.confirmPassword").exists());

        verifyNoInteractions(authService);
    }

    @Test
    void protectedEndpointWithoutTokenReturnsUnauthorized()
            throws Exception {

        mockMvc.perform(get("/api/v1/test/protected"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"));
    }

    @Test
    void protectedEndpointWithInvalidTokenReturnsUnauthorized()
            throws Exception {

        when(jwtService.isTokenValid("invalid-token"))
                .thenReturn(false);

        mockMvc.perform(get("/api/v1/test/protected")
                        .header(
                                "Authorization",
                                "Bearer invalid-token"
                        ))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message")
                        .value("Invalid or expired access token"));
    }

    @Test
    void userCannotAccessAdminEndpoint()
            throws Exception {

        String token = "valid-user-token";
        String email = "user@example.com";

        when(jwtService.isTokenValid(token))
                .thenReturn(true);

        when(jwtService.extractEmail(token))
                .thenReturn(email);

        User user = new User();
        user.setName("Test User");
        user.setEmail(email);
        user.setPassword("not-used-in-this-test");
        user.setStatus("ACTIVE");
        user.setRole(new Role(RoleName.USER));

        when(userRepository.findByEmailWithRole(email))
                .thenReturn(Optional.of(user));

        mockMvc.perform(get("/api/v1/admin/test")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.error").value("Forbidden"));
    }
}