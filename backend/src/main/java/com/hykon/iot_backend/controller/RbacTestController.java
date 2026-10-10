
package com.hykon.iot_backend.controller;

import java.util.Map;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@Tag(
        name = "Role-Based Access Control",
        description = "Endpoints for testing USER and ADMIN permissions"
)
public class RbacTestController {

    @Operation(
            summary = "Test USER access",
            description = "Accessible to authenticated users with the USER or ADMIN role.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @GetMapping("/user/test")
    public Map<String, String> userEndpoint() {
        return Map.of(
                "message",
                "USER or ADMIN access granted"
        );
    }

    @Operation(
            summary = "Test ADMIN access",
            description = "Accessible only to authenticated users with the ADMIN role.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @GetMapping("/admin/test")
    public Map<String, String> adminEndpoint() {
        return Map.of(
                "message",
                "ADMIN access granted"
        );
    }
}
