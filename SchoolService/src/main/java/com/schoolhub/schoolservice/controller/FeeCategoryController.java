package com.schoolhub.schoolservice.controller;

import com.schoolhub.schoolservice.dto.Requests.FeeCategoryReq;
import com.schoolhub.schoolservice.service.FeeCategoryService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/fee-categories")
public class FeeCategoryController {

    private final FeeCategoryService service;

    public FeeCategoryController(FeeCategoryService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','BURSAR')")
    public ResponseEntity<?> list() { return ResponseEntity.ok(service.list()); }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> create(@Valid @RequestBody FeeCategoryReq req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(req));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> deactivate(@PathVariable Long id) {
        service.deactivate(id);
        return ResponseEntity.ok().build();
    }
}
