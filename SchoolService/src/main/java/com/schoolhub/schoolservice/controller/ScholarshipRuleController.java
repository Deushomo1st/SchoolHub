package com.schoolhub.schoolservice.controller;

import com.schoolhub.schoolservice.dto.Requests.ScholarshipRuleReq;
import com.schoolhub.schoolservice.service.ScholarshipRuleService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/scholarship-rules")
public class ScholarshipRuleController {

    private final ScholarshipRuleService service;

    public ScholarshipRuleController(ScholarshipRuleService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','BURSAR')")
    public ResponseEntity<?> list() { return ResponseEntity.ok(service.list()); }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','BURSAR')")
    public ResponseEntity<?> get(@PathVariable Long id) { return ResponseEntity.ok(service.get(id)); }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> create(@Valid @RequestBody ScholarshipRuleReq req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(req));
    }

    @PostMapping("/{id}/evaluate/{studentId}")
    @PreAuthorize("hasAnyRole('ADMIN','BURSAR')")
    public ResponseEntity<?> evaluate(@PathVariable Long id, @PathVariable Long studentId) {
        return ResponseEntity.ok(service.evaluate(id, studentId));
    }

    @PostMapping("/{id}/evaluate-all")
    @PreAuthorize("hasAnyRole('ADMIN','BURSAR')")
    public ResponseEntity<?> evaluateAll(@PathVariable Long id) {
        return ResponseEntity.ok(service.evaluateAll(id));
    }
}
