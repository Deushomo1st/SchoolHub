package com.schoolhub.schoolservice.controller;

import com.schoolhub.schoolservice.dto.Requests.SchedulePeriodReq;
import com.schoolhub.schoolservice.service.SchedulePeriodService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/schedule-periods")
public class SchedulePeriodController {

    private final SchedulePeriodService service;

    public SchedulePeriodController(SchedulePeriodService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<?> list() { return ResponseEntity.ok(service.list()); }

    @GetMapping("/roots")
    public ResponseEntity<?> roots() { return ResponseEntity.ok(service.roots()); }

    @GetMapping("/{id}")
    public ResponseEntity<?> get(@PathVariable Long id) { return ResponseEntity.ok(service.get(id)); }

    @GetMapping("/{id}/children")
    public ResponseEntity<?> children(@PathVariable Long id) { return ResponseEntity.ok(service.children(id)); }

    @GetMapping("/{id}/ancestors")
    public ResponseEntity<?> ancestors(@PathVariable Long id) { return ResponseEntity.ok(service.ancestors(id)); }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> create(@Valid @RequestBody SchedulePeriodReq req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(req));
    }
}
