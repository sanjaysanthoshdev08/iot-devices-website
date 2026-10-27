
package com.hykon.iot_backend.service;

import com.hykon.iot_backend.entity.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.util.Date;

@Service
public class JwtService {

    private final SecretKey secretKey;
    private final long accessTokenExpiration;

    public JwtService(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.access-token-expiration}") long accessTokenExpiration
    ) {
        // Decode the Base64-encoded secret into its original key bytes.
        this.secretKey = Keys.hmacShaKeyFor(
                Decoders.BASE64.decode(secret)
        );

        this.accessTokenExpiration = accessTokenExpiration;
    }

    public String generateAccessToken(User user) {

        Date now = new Date();

        Date expiration = new Date(
                now.getTime() + accessTokenExpiration
        );

        return Jwts.builder()
                .subject(user.getEmail())
                .claim("userId", user.getId())
                .claim("role", user.getRole().getName().name())
                .issuedAt(now)
                .expiration(expiration)
                .signWith(secretKey)
                .compact();
    }

    public String generateAccessToken(User user, java.util.UUID sessionId) {
        if (sessionId == null) {
            throw new IllegalArgumentException("Login session ID is required");
        }

        Date now = new Date();
        Date expiration = new Date(
                now.getTime() + accessTokenExpiration
        );

        return Jwts.builder()
                .subject(user.getEmail())
                .claim("userId", user.getId())
                .claim("role", user.getRole().getName().name())
                .claim("sid", sessionId.toString())
                .issuedAt(now)
                .expiration(expiration)
                .signWith(secretKey)
                .compact();
    }

    public java.util.UUID extractSessionId(String token) {
        String sessionId = extractAllClaims(token)
                .get("sid", String.class);

        if (sessionId == null || sessionId.isBlank()) {
            throw new IllegalArgumentException(
                    "Login session ID is missing"
            );
        }

        return java.util.UUID.fromString(sessionId);
    }

    public String extractEmail(String token) {

        return extractAllClaims(token).getSubject();
    }

    // Extract the user's role from the JWT.
    public String extractRole(String token) {

        return extractAllClaims(token).get("role", String.class);
    }

    public boolean isTokenValid(String token) {

        try {
            Claims claims = extractAllClaims(token);

            return claims.getExpiration().after(new Date());

        } catch (Exception exception) {
            return false;
        }
    }

    private Claims extractAllClaims(String token) {

        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
