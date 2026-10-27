package com.hykon.iot_backend.controller;

import com.hykon.iot_backend.dto.DistributorAccessRequest;
import com.hykon.iot_backend.dto.DistributorAccessResponse;
import com.hykon.iot_backend.response.ApiResponse;
import com.hykon.iot_backend.service.DistributorAccessService;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.security.Principal;

@RestController
@RequestMapping("/api/v1/distributor/access")
public class DistributorAccessController {

    private final DistributorAccessService accessService;

    public DistributorAccessController(
            DistributorAccessService accessService
    ) {
        this.accessService = accessService;
    }

    @PostMapping("/verify")
    public ResponseEntity<ApiResponse<DistributorAccessResponse>> verify(
            @Valid @RequestBody DistributorAccessRequest request,
            Principal principal
    ) {
        if (principal == null
                || principal.getName() == null
                || principal.getName().isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Authentication is required"
            );
        }

        DistributorAccessResponse response = accessService.verify(
                request,
                principal.getName()
        );

        return ResponseEntity.ok(ApiResponse.success(
                "Distributor verified successfully",
                response
        ));
    }
}