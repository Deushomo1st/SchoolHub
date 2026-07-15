package com.schoolhub.schoolservice.controller;

import com.schoolhub.schoolservice.dto.Requests.ResourceReq;
import com.schoolhub.schoolservice.service.ResourceService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/resources")
public class ResourceController {

    private final ResourceService service;

    public ResourceController(ResourceService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<?> list() { return ResponseEntity.ok(service.list()); }

    @GetMapping("/{id}")
    public ResponseEntity<?> get(@PathVariable Long id) { return ResponseEntity.ok(service.get(id)); }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> create(@Valid @RequestBody ResourceReq req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(req));
    }

    // Overrides a staff_only Resource for one specific student.
    @PostMapping("/{id}/permits/{userId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> grantPermit(@PathVariable Long id, @PathVariable Long userId) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.grantPermit(id, userId));
    }
}
