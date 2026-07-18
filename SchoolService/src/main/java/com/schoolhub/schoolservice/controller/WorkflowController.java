package com.schoolhub.schoolservice.controller;

import com.schoolhub.schoolservice.dto.Requests.GuardianClaimReq;
import com.schoolhub.schoolservice.dto.Requests.OfferingReq;
import com.schoolhub.schoolservice.dto.Requests.OrgUnitReq;
import com.schoolhub.schoolservice.dto.Requests.ProgressionRuleReq;
import com.schoolhub.schoolservice.dto.Requests.ProtestReq;
import com.schoolhub.schoolservice.service.WorkflowService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/workflow-requests")
public class WorkflowController {

    private final WorkflowService service;

    public WorkflowController(WorkflowService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','MODERATOR')")
    public ResponseEntity<?> list() { return ResponseEntity.ok(service.list()); }

    @GetMapping("/{id}")
    public ResponseEntity<?> get(@PathVariable Long id) { return ResponseEntity.ok(service.get(id)); }

    // Any signed-in tenant user can propose; an Admin proposing gets applied immediately
    // (standing permission), everyone else lands pending an Admin's confirm.
    @PostMapping("/org-units")
    public ResponseEntity<?> proposeOrgUnit(@Valid @RequestBody OrgUnitReq req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.proposeOrgUnitCreate(req));
    }

    @PostMapping("/offerings")
    public ResponseEntity<?> proposeOffering(@Valid @RequestBody OfferingReq req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.proposeOfferingCreate(req));
    }

    @PostMapping("/progression-rules")
    public ResponseEntity<?> proposeProgressionRule(@Valid @RequestBody ProgressionRuleReq req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.proposeProgressionRuleCreate(req));
    }

    // A guardian claiming a child by handle - always lands pending an Admin's confirm.
    @PostMapping("/guardian-links")
    @PreAuthorize("hasRole('PARENT')")
    public ResponseEntity<?> proposeGuardianLink(@Valid @RequestBody GuardianClaimReq req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.initiateGuardianChildLink(req));
    }

    // Blanket gate is ADMIN-or-MODERATOR; WorkflowService.requireConfirmAuthority() enforces the
    // one type-specific rule (CREDENTIAL_REVOKE_REQUEST needs Moderator specifically, since an
    // Admin confirming their own revocation request would defeat the point).
    @PostMapping("/{id}/confirm")
    @PreAuthorize("hasAnyRole('ADMIN','MODERATOR')")
    public ResponseEntity<?> confirm(@PathVariable Long id) { return ResponseEntity.ok(service.confirm(id)); }

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasAnyRole('ADMIN','MODERATOR')")
    public ResponseEntity<?> reject(@PathVariable Long id) {
        service.reject(id);
        return ResponseEntity.ok().build();
    }

    // ---- Protests: anyone notified can raise one; only a moderator/admin can resolve it ----

    @GetMapping("/{id}/protests")
    public ResponseEntity<?> protests(@PathVariable Long id) { return ResponseEntity.ok(service.protestsFor(id)); }

    @PostMapping("/{id}/protests")
    public ResponseEntity<?> raiseProtest(@PathVariable Long id, @RequestBody(required = false) ProtestReq req) {
        String comment = req == null ? null : req.comment();
        return ResponseEntity.status(HttpStatus.CREATED).body(service.raiseProtest(id, comment));
    }

    // A regular member's second ESCALATES (tier 1->2, notifies Moderators) rather than
    // cancelling outright; only a Moderator's second (or one at tier>=2) is final. See
    // WorkflowService.secondProtest().
    @PostMapping("/protests/{protestId}/second")
    @PreAuthorize("hasAnyRole('ADMIN','MODERATOR')")
    public ResponseEntity<?> secondProtest(@PathVariable Long protestId) {
        service.secondProtest(protestId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/protests/{protestId}/dismiss")
    @PreAuthorize("hasAnyRole('ADMIN','MODERATOR')")
    public ResponseEntity<?> dismissProtest(@PathVariable Long protestId) {
        service.dismissProtest(protestId);
        return ResponseEntity.ok().build();
    }
}
