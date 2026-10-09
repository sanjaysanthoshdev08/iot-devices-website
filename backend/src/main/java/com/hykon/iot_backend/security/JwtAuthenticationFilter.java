
package com.hykon.iot_backend.security;

import com.hykon.iot_backend.entity.User;
import com.hykon.iot_backend.repository.UserRepository;
import com.hykon.iot_backend.service.JwtService;
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

public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserRepository userRepository;

    public JwtAuthenticationFilter(
            JwtService jwtService,
            UserRepository userRepository
    ) {
        this.jwtService = jwtService;
        this.userRepository = userRepository;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        String authorizationHeader =
                request.getHeader("Authorization");

        // No token: let Spring Security decide whether
        // this endpoint requires authentication.
        if (authorizationHeader == null) {
            filterChain.doFilter(request, response);
            return;
        }

        // A supplied Authorization header must use Bearer format.
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

        try {
            // Validate the JWT signature and expiration.
            if (!jwtService.isTokenValid(token)) {
                sendUnauthorized(response);
                return;
            }

            // Identify the user from the signed token.
            String email = jwtService.extractEmail(token);

            if (email == null || email.isBlank()) {
                sendUnauthorized(response);
                return;
            }

            // Load the user and current role from PostgreSQL.
            User user = userRepository.findByEmailWithRole(email)
                    .orElse(null);

            // Reject deleted users and inactive accounts.
            if (user == null
                    || !"ACTIVE".equalsIgnoreCase(user.getStatus())
                    || user.getRole() == null
                    || user.getRole().getName() == null) {
                sendUnauthorized(response);
                return;
            }

            // Use the current database role, not the JWT role claim.
            String currentRole = user.getRole().getName().name();

            var authorities = List.of(
                    new SimpleGrantedAuthority("ROLE_" + currentRole)
            );

            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(
                            user.getEmail(),
                            null,
                            authorities
                    );

            SecurityContextHolder
                    .getContext()
                    .setAuthentication(authentication);

        } catch (Exception exception) {
            SecurityContextHolder.clearContext();
            sendUnauthorized(response);
            return;
        }

        filterChain.doFilter(request, response);
    }

    private void sendUnauthorized(HttpServletResponse response)
            throws IOException {

        SecurityContextHolder.clearContext();

        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        response.getWriter().write(
                "{\"status\":401,\"error\":\"Unauthorized\","
                        + "\"message\":\"Invalid or expired access token\"}"
        );
    }
}