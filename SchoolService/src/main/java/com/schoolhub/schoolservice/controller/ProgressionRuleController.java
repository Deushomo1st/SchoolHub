package com.schoolhub.schoolservice.controller;

import com.schoolhub.schoolservice.dto.Requests.EvaluateReq;
import com.schoolhub.schoolservice.dto.Requests.ProgressionRuleReq;
import com.schoolhub.schoolservice.service.ProgressionRuleService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/progression-rules")
public class ProgressionRuleController {

    private final ProgressionRuleService service;

    public ProgressionRuleController(ProgressionRuleService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER')")
    public ResponseEntity<?> list() { return ResponseEntity.ok(service.list()); }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER')")
    public ResponseEntity<?> get(@PathVariable Long id) { return ResponseEntity.ok(service.get(id)); }

    // Direct creation for an Admin. A non-Admin's propose-then-confirm path is
    // POST /api/v1/workflow-requests/progression-rules (WorkflowController) - same underlying create().
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> create(@Valid @RequestBody ProgressionRuleReq req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(req));
    }

    @PostMapping("/{id}/evaluate/{enrollmentId}")
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER')")
    public ResponseEntity<?> evaluate(@PathVariable Long id, @PathVariable Long enrollmentId,
                                      @RequestBody(required = false) EvaluateReq req) {
        Double score = req == null ? null : req.manualScore();
        return ResponseEntity.ok(service.evaluate(id, enrollmentId, score));
    }

    @PostMapping("/{id}/evaluate-cohort/{cohortId}")
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER')")
    public ResponseEntity<?> evaluateForCohort(@PathVariable Long id, @PathVariable Long cohortId) {
        return ResponseEntity.ok(service.evaluateForCohort(id, cohortId));
    }
}
