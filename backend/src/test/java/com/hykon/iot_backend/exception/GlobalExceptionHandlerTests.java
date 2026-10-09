package com.hykon.iot_backend.exception;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GlobalExceptionHandlerTests {

    private GlobalExceptionHandler handler;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler();
    }

    @Test
    void duplicateResourceReturnsConflict() {
        ResponseEntity<Map<String, Object>> response =
                handler.handleDuplicateResource(
                        new DuplicateResourceException("Email already exists")
                );

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertEquals(409, response.getBody().get("status"));
        assertEquals("Conflict", response.getBody().get("error"));
        assertEquals(
                "Email already exists",
                response.getBody().get("message")
        );
    }

    @Test
    void invalidCredentialsReturnsUnauthorized() {
        ResponseEntity<Map<String, Object>> response =
                handler.handleInvalidCredentials(
                        new InvalidCredentialsException("Sensitive detail")
                );

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertEquals(401, response.getBody().get("status"));
        assertEquals("Unauthorized", response.getBody().get("error"));
        assertEquals(
                "Invalid email or password.",
                response.getBody().get("message")
        );
        assertFalse(
                response.getBody().get("message").toString()
                        .contains("Sensitive detail")
        );
    }

    @Test
    void invalidTokenReturnsUnauthorized() {
        ResponseEntity<Map<String, Object>> response =
                handler.handleInvalidToken(
                        new InvalidTokenException("Sensitive token detail")
                );

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertEquals(
                "The token is invalid, expired, or revoked.",
                response.getBody().get("message")
        );
    }

    @Test
    void missingResourceReturnsNotFound() {
        ResponseEntity<Map<String, Object>> response =
                handler.handleResourceNotFound(
                        new ResourceNotFoundException("Device not found")
                );

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertEquals(404, response.getBody().get("status"));
        assertEquals("Device not found", response.getBody().get("message"));
    }

    @Test
    void databaseIntegrityViolationReturnsSafeConflictMessage() {
        ResponseEntity<Map<String, Object>> response =
                handler.handleDataIntegrityViolation(
                        new org.springframework.dao.DataIntegrityViolationException(
                                "Internal database detail"
                        )
                );

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertEquals(
                "The request conflicts with existing data.",
                response.getBody().get("message")
        );
    }

    @Test
    void databaseFailureReturnsSafeServerError() {
        ResponseEntity<Map<String, Object>> response =
                handler.handleDatabaseException(
                        new DataAccessResourceFailureException(
                                "Internal database detail"
                        )
                );

        assertEquals(
                HttpStatus.INTERNAL_SERVER_ERROR,
                response.getStatusCode()
        );
        assertEquals(
                "A database operation could not be completed.",
                response.getBody().get("message")
        );
    }

    @Test
    void illegalArgumentReturnsBadRequest() {
        ResponseEntity<Map<String, Object>> response =
                handler.handleIllegalArgument(
                        new IllegalArgumentException("Passwords do not match")
                );

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals(
                "Passwords do not match",
                response.getBody().get("message")
        );
    }

    @Test
    void responseStatusExceptionPreservesStatus() {
        ResponseStatusException exception =
                new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Requested resource not found"
                );

        ResponseEntity<Map<String, Object>> response =
                handler.handleResponseStatus(exception);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertEquals(
                "Requested resource not found",
                response.getBody().get("message")
        );
    }

    @Test
    void unexpectedExceptionReturnsGenericServerError() {
        ResponseEntity<Map<String, Object>> response =
                handler.handleUnexpectedException(
                        new RuntimeException("Sensitive internal detail")
                );

        assertEquals(
                HttpStatus.INTERNAL_SERVER_ERROR,
                response.getStatusCode()
        );
        assertEquals(
                "An unexpected server error occurred.",
                response.getBody().get("message")
        );
        assertFalse(
                response.getBody().get("message").toString()
                        .contains("Sensitive internal detail")
        );
    }

    @Test
    void errorResponsesContainStatusErrorAndMessage() {
        ResponseEntity<Map<String, Object>> response =
                handler.handleResourceNotFound(
                        new ResourceNotFoundException("Not found")
                );

        Map<String, Object> body = response.getBody();

        assertTrue(body.containsKey("status"));
        assertTrue(body.containsKey("error"));
        assertTrue(body.containsKey("message"));
    }
}