
package com.hykon.iot_backend.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/test")
@Tag(
        name = "JWT Authentication",
        description = "Endpoints for verifying JWT-protected access"
)
public class JwtTestController {

    @Operation(
            summary = "Verify JWT authentication",
            description = "Returns a success message when a valid JWT access token is supplied.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @GetMapping("/protected")
    public Map<String, String> protectedEndpoint() {
        return Map.of(
                "message", "JWT authentication successful"
        );
    }
}
