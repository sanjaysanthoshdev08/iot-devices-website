package com.hykon.iot_backend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Security chain for the universal telemetry endpoint. Same rules as the v1 device chain:
 * stateless, only this POST is reachable, and the service itself requires a valid device credential.
 */
@Configuration
public class DeviceTelemetryV2SecurityConfig {

    public static final String PATH = "/api/v2/telemetry/readings";

    @Bean
    @Order(2)
    public SecurityFilterChain deviceTelemetryV2SecurityFilterChain(
            HttpSecurity http
    ) throws Exception {
        http
            .securityMatcher(request -> request.getRequestURI().equals(
                    request.getContextPath() + PATH))
            .cors(cors -> cors.disable())
            .csrf(csrf -> csrf.disable())
            .formLogin(form -> form.disable())
            .httpBasic(basic -> basic.disable())
            .requestCache(cache -> cache.disable())
            .sessionManagement(session -> session.sessionCreationPolicy(
                    SessionCreationPolicy.STATELESS))
            .exceptionHandling(exceptions -> exceptions
                .authenticationEntryPoint((request, response, exception) -> {
                    response.setStatus(401);
                    response.setContentType("application/json");
                    response.setCharacterEncoding("UTF-8");
                    response.getWriter().write(
                            "{\"success\":false,\"status\":401,"
                            + "\"message\":\"Device authentication is required\"}");
                })
                .accessDeniedHandler((request, response, exception) -> {
                    response.setStatus(403);
                    response.setContentType("application/json");
                    response.setCharacterEncoding("UTF-8");
                    response.getWriter().write(
                            "{\"success\":false,\"status\":403,"
                            + "\"message\":\"Request method is not permitted\"}");
                }))
            .authorizeHttpRequests(authorize -> authorize
                .requestMatchers(HttpMethod.POST, PATH).permitAll()
                .anyRequest().denyAll());
        return http.build();
    }
}
