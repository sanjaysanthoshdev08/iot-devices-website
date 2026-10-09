
package com.hykon.iot_backend.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/test")
public class JwtTestController {

    @GetMapping("/protected")
    public Map<String, String> protectedEndpoint() {
        return Map.of(
                "message", "JWT authentication successful"
        );
    }
}