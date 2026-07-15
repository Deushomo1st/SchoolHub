package com.schoolhub.authservice.controller;

import com.schoolhub.authservice.dto.BootstrapRequest;
import com.schoolhub.authservice.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Bootstrap surface - only useful on a fresh database (zero users).
 * Creates the first PLATFORM_OWNER, then auto-disables once any user exists.
 */
@RestController
@RequestMapping("/api/v1/auth/setup")
public class SetupController {

    private final AuthService authService;

    public SetupController(AuthService authService) {
        this.authService = authService;
    }

    @GetMapping("/status")
    public ResponseEntity<?> status() {
        return ResponseEntity.ok(Map.of("needs_bootstrap", authService.needsBootstrap()));
    }

    @PostMapping("/bootstrap")
    public ResponseEntity<?> bootstrap(@Valid @RequestBody BootstrapRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.bootstrap(req));
    }
}
