package com.hykon.iot_backend.controller;

import com.hykon.iot_backend.dto.DistributorAccessRequest;
import com.hykon.iot_backend.dto.DistributorAccessResponse;
import com.hykon.iot_backend.response.ApiResponse;
import com.hykon.iot_backend.service.SessionAccessVerificationService;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/distributor/access")
public class DistributorAccessController {

    private final SessionAccessVerificationService accessService;

    public DistributorAccessController(
            SessionAccessVerificationService accessService
    ) {
        this.accessService = accessService;
    }

    @PostMapping("/verify")
    public ResponseEntity<ApiResponse<DistributorAccessResponse>> verify(
            @Valid @RequestBody DistributorAccessRequest request,
            Authentication authentication
    ) {
        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication.getName() == null
                || authentication.getName().isBlank()
                || !(authentication.getDetails() instanceof UUID sessionId)) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "An authenticated login session is required"
            );
        }

        DistributorAccessResponse response =
                accessService.verifyDistributor(
                        request,
                        authentication.getName(),
                        sessionId
                );

        return ResponseEntity.ok(ApiResponse.success(
                "Distributor verified successfully",
                response
        ));
    }
}