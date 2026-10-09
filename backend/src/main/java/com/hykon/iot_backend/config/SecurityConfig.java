package com.hykon.iot_backend.config;

import com.hykon.iot_backend.repository.UserRepository;
import com.hykon.iot_backend.security.JwtAuthenticationFilter;
import com.hykon.iot_backend.service.JwtService;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
public class SecurityConfig {

@Bean
public JwtAuthenticationFilter jwtAuthenticationFilter(
        JwtService jwtService,
        UserRepository userRepository
) {
    return new JwtAuthenticationFilter(jwtService, userRepository);
}

// Keep the JWT filter registered only in Spring Security's filter chain.
@Bean
public FilterRegistrationBean<JwtAuthenticationFilter>
jwtAuthenticationFilterRegistration(
        JwtAuthenticationFilter jwtAuthenticationFilter
) {
    FilterRegistrationBean<JwtAuthenticationFilter> registration =
            new FilterRegistrationBean<>(jwtAuthenticationFilter);

    registration.setEnabled(false);

    return registration;
}

@Bean
public SecurityFilterChain securityFilterChain(
        HttpSecurity http,
        JwtAuthenticationFilter jwtAuthenticationFilter
) throws Exception {

    http
        .csrf(csrf -> csrf.disable())
        .formLogin(form -> form.disable())
        .httpBasic(basic -> basic.disable())
        .sessionManagement(session ->
            session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
        )
        .exceptionHandling(exceptions ->
            exceptions.authenticationEntryPoint(
                (request, response, authException) -> {
                    response.setStatus(HttpStatus.UNAUTHORIZED.value());
                    response.setContentType("application/json");
                    response.setCharacterEncoding("UTF-8");
                    response.getWriter().write(
                        "{\"status\":401,\"error\":\"Unauthorized\","
                        + "\"message\":\"Authentication is required\"}"
                    );
                }
            )
        )
        .authorizeHttpRequests(auth -> auth
            // Public authentication endpoints: POST only.
            .requestMatchers(
                HttpMethod.POST,
                "/api/v1/auth/register",
                "/api/v1/auth/login",
                "/api/v1/auth/refresh",
                "/api/v1/auth/logout"
            ).permitAll()

            // Permit Spring Boot's error endpoint.
            .requestMatchers("/error").permitAll()

            // All remaining endpoints require authentication.
            .anyRequest().authenticated()
        )
        .addFilterBefore(
            jwtAuthenticationFilter,
            UsernamePasswordAuthenticationFilter.class
        );

    return http.build();
}
}
