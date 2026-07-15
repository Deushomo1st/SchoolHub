package com.schoolhub.tenantservice.controller;

import com.schoolhub.tenantservice.dto.SignupRequest;
import com.schoolhub.tenantservice.service.TenantService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/tenants")
public class TenantController {

    private final TenantService tenantService;

    public TenantController(TenantService tenantService) {
        this.tenantService = tenantService;
    }

    @GetMapping("/templates")
    public ResponseEntity<?> templates() {
        return ResponseEntity.ok(tenantService.templates());
    }

    @GetMapping("/plans")
    public ResponseEntity<?> plans() {
        return ResponseEntity.ok(tenantService.plans());
    }

    @PostMapping("/signup")
    public ResponseEntity<?> signup(@Valid @RequestBody SignupRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(tenantService.signup(req));
    }
}
