
package com.hykon.iot_backend.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(
    info = @Info(
        title = "HYKON Inverter / UPS IoT Monitoring API",
        version = "v1",
        description = """
            REST API for the HYKON Inverter / UPS IoT Monitoring System.

            Includes user registration, login, token refresh, logout,
            JWT authentication testing, and role-based access control.

            Public authentication endpoints do not require an access token.
            Protected endpoints require a valid Bearer JWT access token.
            """,
        contact = @Contact(
            name = "HYKON IoT Backend Team"
        )
    )
)
@SecurityScheme(
    name = "bearerAuth",
    type = SecuritySchemeType.HTTP,
    scheme = "bearer",
    bearerFormat = "JWT",
    description = "Enter a valid JWT access token. Do not include the 'Bearer ' prefix."
)
public class OpenApiConfig {
}
