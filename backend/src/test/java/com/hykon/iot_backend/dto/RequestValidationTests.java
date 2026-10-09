package com.hykon.iot_backend.dto;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class RequestValidationTests {

    @Test
    void registrationRejectsInvalidFields() {
        RegisterRequest request = new RegisterRequest();

        request.setName(" ");
        request.setEmail("invalid-email");
        request.setPassword("short");
        request.setConfirmPassword("");

        try (ValidatorFactory factory =
                     Validation.buildDefaultValidatorFactory()) {

            Validator validator = factory.getValidator();
            var violations = validator.validate(request);

            assertTrue(violations.stream().anyMatch(
                    v -> v.getPropertyPath().toString().equals("name")));

            assertTrue(violations.stream().anyMatch(
                    v -> v.getPropertyPath().toString().equals("email")));

            assertTrue(violations.stream().anyMatch(
                    v -> v.getPropertyPath().toString().equals("password")));

            assertTrue(violations.stream().anyMatch(
                    v -> v.getPropertyPath().toString()
                            .equals("confirmPassword")));
        }
    }

    @Test
    void loginRejectsInvalidFields() {
        LoginRequest request = new LoginRequest();

        request.setEmail("invalid-email");
        request.setPassword("");

        try (ValidatorFactory factory =
                     Validation.buildDefaultValidatorFactory()) {

            Validator validator = factory.getValidator();
            var violations = validator.validate(request);

            assertTrue(violations.stream().anyMatch(
                    v -> v.getPropertyPath().toString().equals("email")));

            assertTrue(violations.stream().anyMatch(
                    v -> v.getPropertyPath().toString().equals("password")));
        }
    }

    @Test
    void refreshTokenRequestRejectsBlankToken() {
        RefreshTokenRequest request = new RefreshTokenRequest();

        request.setRefreshToken("   ");

        try (ValidatorFactory factory =
                     Validation.buildDefaultValidatorFactory()) {

            Validator validator = factory.getValidator();
            var violations = validator.validate(request);

            assertTrue(violations.stream().anyMatch(
                    v -> v.getPropertyPath().toString()
                            .equals("refreshToken")));
        }
    }
}