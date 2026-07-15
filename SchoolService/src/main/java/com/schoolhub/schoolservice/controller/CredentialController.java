package com.schoolhub.schoolservice.controller;

import com.schoolhub.schoolservice.dto.Requests.CredentialReq;
import com.schoolhub.schoolservice.service.CredentialService;
import com.schoolhub.schoolservice.service.WorkflowService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/credentials")
public class CredentialController {

    private final CredentialService service;
    private final WorkflowService workflow;

    public CredentialController(CredentialService service, WorkflowService workflow) {
        this.service = service;
        this.workflow = workflow;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER')")
    public ResponseEntity<?> list(@RequestParam Long personUserId) {
        return ResponseEntity.ok(service.listForPerson(personUserId));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER')")
    public ResponseEntity<?> get(@PathVariable Long id) { return ResponseEntity.ok(service.get(id)); }

    // The "external sharing screen" idea, scaled to what's buildable: authenticated (not
    // anonymous), curated to a few safe fields - see the class-level note on why a truly public,
    // unauthenticated verification endpoint doesn't fit this system's JWT-based tenant routing.
    @GetMapping("/{id}/verify")
    public ResponseEntity<?> verify(@PathVariable Long id) { return ResponseEntity.ok(service.verify(id)); }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> issue(@Valid @RequestBody CredentialReq req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.issue(req));
    }

    // Doesn't revoke synchronously - opens a Moderator-reviewed request (see WorkflowService).
    @PostMapping("/{id}/revoke-request")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> requestRevoke(@PathVariable Long id, @RequestParam(required = false) String reason) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(workflow.initiateCredentialRevoke(id, reason));
    }
}
