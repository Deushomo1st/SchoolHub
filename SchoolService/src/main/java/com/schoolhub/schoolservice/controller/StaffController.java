package com.schoolhub.schoolservice.controller;

import com.schoolhub.schoolservice.dto.Requests.StaffReq;
import com.schoolhub.schoolservice.service.AccountProvisioning;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Set;

/** School admin creates non-teaching staff logins (admin, principal, bursar). */
@RestController
@RequestMapping("/api/v1/staff")
public class StaffController {

    private static final Set<String> ALLOWED = Set.of("ADMIN", "PRINCIPAL", "BURSAR");

    private final AccountProvisioning provisioning;

    public StaffController(AccountProvisioning provisioning) {
        this.provisioning = provisioning;
    }

    // PRINCIPAL inherits ROLE_ADMIN at the filter, so principals can add staff too.
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> create(@Valid @RequestBody StaffReq req) {
        String role = req.role() == null ? "" : req.role().trim().toUpperCase();
        if (!ALLOWED.contains(role)) {
            throw new IllegalArgumentException("Staff role must be one of " + ALLOWED);
        }
        Long id = provisioning.createLogin(req.email(), req.password(), req.firstName(), req.lastName(), req.phone(), role);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("id", id, "email", req.email(), "role", role));
    }
}
