package com.hykon.iot_backend.security;

import com.hykon.iot_backend.entity.User;
import com.hykon.iot_backend.exception.InvalidTokenException;
import com.hykon.iot_backend.repository.UserRepository;
import com.hykon.iot_backend.service.JwtService;
import com.hykon.iot_backend.service.LoginSessionService;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserRepository userRepository;
    private final LoginSessionService loginSessionService;

    public JwtAuthenticationFilter(
            JwtService jwtService,
            UserRepository userRepository,
            LoginSessionService loginSessionService
    ) {
        this.jwtService = jwtService;
        this.userRepository = userRepository;
        this.loginSessionService = loginSessionService;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        String authorizationHeader = request.getHeader("Authorization");

        if (authorizationHeader == null) {
            filterChain.doFilter(request, response);
            return;
        }

        if (!authorizationHeader.startsWith("Bearer ")
                || authorizationHeader.length() <= 7) {
            sendUnauthorized(response);
            return;
        }

        String token = authorizationHeader.substring(7).trim();

        if (token.isEmpty()) {
            sendUnauthorized(response);
            return;
        }

        final String email;
        final UUID sessionId;

        try {
            if (!jwtService.isTokenValid(token)) {
                sendUnauthorized(response);
                return;
            }

            email = jwtService.extractEmail(token);
            sessionId = jwtService.extractSessionId(token);
        } catch (io.jsonwebtoken.JwtException
                 | IllegalArgumentException exception) {
            sendUnauthorized(response);
            return;
        }

        if (email == null || email.isBlank() || sessionId == null) {
            sendUnauthorized(response);
            return;
        }

        // Keep account status and role authoritative in PostgreSQL.
        User user = userRepository.findByEmailWithRole(email)
                .orElse(null);

        if (user == null
                || !"ACTIVE".equalsIgnoreCase(user.getStatus())
                || user.getRole() == null
                || user.getRole().getName() == null) {
            sendUnauthorized(response);
            return;
        }

        // Only invalid sessions become 401; database failures propagate.
        try {
            loginSessionService.requireActive(sessionId, user.getId());
        } catch (InvalidTokenException exception) {
            sendUnauthorized(response);
            return;
        }

        String currentRole = user.getRole().getName().name();

        var authorities = List.of(
                new SimpleGrantedAuthority("ROLE_" + currentRole)
        );

        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(
                        user.getEmail(), null, authorities
                );

        // Make the validated session ID available to gate enforcement.
        authentication.setDetails(sessionId);

        SecurityContextHolder.getContext()
                .setAuthentication(authentication);

        filterChain.doFilter(request, response);
    }

    private void sendUnauthorized(HttpServletResponse response)
            throws IOException {

        SecurityContextHolder.clearContext();
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        response.getWriter().write(
                "{\"success\":false,\"status\":401,\"error\":\"Unauthorized\","
                        + "\"message\":\"Invalid or expired access token\"}"
        );
    }
}