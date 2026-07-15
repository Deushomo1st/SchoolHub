package com.schoolhub.schoolservice.controller;

import com.schoolhub.schoolservice.dto.Requests.OrgUnitReq;
import com.schoolhub.schoolservice.service.OrgUnitService;
import com.schoolhub.schoolservice.service.WorkflowService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/org-units")
public class OrgUnitController {

    private final OrgUnitService service;
    private final WorkflowService workflow;

    public OrgUnitController(OrgUnitService service, WorkflowService workflow) {
        this.service = service;
        this.workflow = workflow;
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

    // Direct creation for an Admin. A non-Admin's "propose, then the owner confirms" path is
    // POST /api/v1/workflow-requests/org-units (WorkflowController) - same underlying create().
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> create(@Valid @RequestBody OrgUnitReq req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(req));
    }

    // Doesn't delete synchronously - flips the unit to pending_deletion and opens a 7-day
    // protest window (WorkflowService.initiateOrgUnitDelete). The actual row is removed only
    // once that window closes uncontested, or immediately if a protest is later dismissed past
    // its deadline.
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> delete(@PathVariable Long id) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(workflow.initiateOrgUnitDelete(id));
    }
}
