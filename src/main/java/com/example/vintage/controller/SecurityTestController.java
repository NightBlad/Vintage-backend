package com.example.vintage.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/security")
public class SecurityTestController {

    @GetMapping("/headers")
    public ResponseEntity<Map<String, String>> testSecurityHeaders() {
        Map<String, String> response = new HashMap<>();
        response.put("message", "Security headers test endpoint");
        response.put("status", "active");
        response.put("timestamp", java.time.LocalDateTime.now().toString());

        return ResponseEntity.ok()
                .header("X-Custom-Header", "Security-Test")
                .body(response);
    }

    @GetMapping("/csrf")
    public ResponseEntity<Map<String, String>> testCsrfProtection() {
        Map<String, String> response = new HashMap<>();
        response.put("message", "CSRF protection is active");
        response.put("status", "protected");

        return ResponseEntity.ok(response);
    }
}
