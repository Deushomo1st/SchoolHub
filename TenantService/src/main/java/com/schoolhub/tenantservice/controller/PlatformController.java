package com.schoolhub.tenantservice.controller;

import com.schoolhub.tenantservice.dto.ModeratorRequest;
import com.schoolhub.tenantservice.dto.PlanRequest;
import com.schoolhub.tenantservice.service.AuditService;
import com.schoolhub.tenantservice.service.PlatformService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/** Super-admin / moderator control plane over all schools. */
@RestController
@RequestMapping("/api/v1/tenants")
public class PlatformController {

    private final PlatformService platform;
    private final AuditService audit;

    public PlatformController(PlatformService platform, AuditService audit) {
        this.platform = platform;
        this.audit = audit;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('PLATFORM_OWNER','MODERATOR')")
    public ResponseEntity<?> listSchools() {
        return ResponseEntity.ok(platform.listSchools());
    }

    @GetMapping("/stats")
    @PreAuthorize("hasAnyRole('PLATFORM_OWNER','MODERATOR')")
    public ResponseEntity<?> stats() {
        return ResponseEntity.ok(platform.stats());
    }

    @GetMapping("/users")
    @PreAuthorize("hasAnyRole('PLATFORM_OWNER','MODERATOR')")
    public ResponseEntity<?> users() {
        return ResponseEntity.ok(platform.listUsers());
    }

    @GetMapping("/audit")
    @PreAuthorize("hasAnyRole('PLATFORM_OWNER','MODERATOR')")
    public ResponseEntity<?> auditTrail() {
        return ResponseEntity.ok(audit.list());
    }

    @PostMapping("/{id}/suspend")
    @PreAuthorize("hasAnyRole('PLATFORM_OWNER','MODERATOR')")
    public ResponseEntity<?> suspend(@PathVariable Long id, Authentication auth) {
        return ResponseEntity.ok(platform.setStatus(id, "suspended", (Long) auth.getPrincipal()));
    }

    @PostMapping("/{id}/activate")
    @PreAuthorize("hasAnyRole('PLATFORM_OWNER','MODERATOR')")
    public ResponseEntity<?> activate(@PathVariable Long id, Authentication auth) {
        return ResponseEntity.ok(platform.setStatus(id, "active", (Long) auth.getPrincipal()));
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasAnyRole('PLATFORM_OWNER','MODERATOR')")
    public ResponseEntity<?> reject(@PathVariable Long id, Authentication auth) {
        return ResponseEntity.ok(platform.setStatus(id, "rejected", (Long) auth.getPrincipal()));
    }

    @GetMapping("/moderators")
    @PreAuthorize("hasAnyRole('PLATFORM_OWNER','MODERATOR')")
    public ResponseEntity<?> listModerators() {
        return ResponseEntity.ok(platform.listModerators());
    }

    @PostMapping("/moderators")
    @PreAuthorize("hasRole('PLATFORM_OWNER')")
    public ResponseEntity<?> createModerator(@Valid @RequestBody ModeratorRequest req, Authentication auth) {
        return ResponseEntity.status(HttpStatus.CREATED).body(platform.createModerator(req, (Long) auth.getPrincipal()));
    }

    // ---- Pricing deck (owner only) ----
    @PostMapping("/plans")
    @PreAuthorize("hasRole('PLATFORM_OWNER')")
    public ResponseEntity<?> createPlan(@Valid @RequestBody PlanRequest req, Authentication auth) {
        return ResponseEntity.status(HttpStatus.CREATED).body(platform.createPlan(req, (Long) auth.getPrincipal()));
    }

    @PutMapping("/plans/{id}")
    @PreAuthorize("hasRole('PLATFORM_OWNER')")
    public ResponseEntity<?> updatePlan(@PathVariable Long id, @Valid @RequestBody PlanRequest req, Authentication auth) {
        return ResponseEntity.ok(platform.updatePlan(id, req, (Long) auth.getPrincipal()));
    }

    @DeleteMapping("/plans/{id}")
    @PreAuthorize("hasRole('PLATFORM_OWNER')")
    public ResponseEntity<?> deletePlan(@PathVariable Long id, Authentication auth) {
        platform.deletePlan(id, (Long) auth.getPrincipal());
        return ResponseEntity.ok().build();
    }
}
