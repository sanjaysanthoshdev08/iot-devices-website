
package com.hykon.iot_backend.controller;

import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class RbacTestController {

    @GetMapping("/user/test")
    public Map<String, String> userEndpoint() {
        return Map.of(
            "message",
            "USER or ADMIN access granted"
        );
    }

    @GetMapping("/admin/test")
    public Map<String, String> adminEndpoint() {
        return Map.of(
            "message",
            "ADMIN access granted"
        );
    }
}