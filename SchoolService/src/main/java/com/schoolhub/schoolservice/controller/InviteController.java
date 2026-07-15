package com.schoolhub.schoolservice.controller;

import com.schoolhub.schoolservice.dto.Requests.ClassJoinIssueReq;
import com.schoolhub.schoolservice.dto.Requests.ClassJoinRedeemReq;
import com.schoolhub.schoolservice.dto.Requests.GuardianLinkReq;
import com.schoolhub.schoolservice.service.InviteService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/invites")
public class InviteController {

    private final InviteService service;

    public InviteController(InviteService service) {
        this.service = service;
    }

    // A teacher hands this code out to their class; a student redeems it below.
    @PostMapping("/class-join")
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER')")
    public ResponseEntity<?> issueClassJoin(@Valid @RequestBody ClassJoinIssueReq req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.issueClassJoin(req.cohortId(), req.maxRedemptions()));
    }

    @PostMapping("/class-join/redeem")
    public ResponseEntity<?> redeemClassJoin(@Valid @RequestBody ClassJoinRedeemReq req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.redeemClassJoin(req.code()));
    }

    // Targeted - no code. The request lands directly on the ward's own account to accept/reject.
    @PostMapping("/guardian-link")
    public ResponseEntity<?> linkGuardianToWard(@Valid @RequestBody GuardianLinkReq req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.linkGuardianToWard(req.wardUserId()));
    }

    @GetMapping("/incoming")
    public ResponseEntity<?> incoming() { return ResponseEntity.ok(service.incoming()); }

    @PostMapping("/redemptions/{id}/confirm")
    public ResponseEntity<?> confirm(@PathVariable Long id) { return ResponseEntity.ok(service.confirm(id)); }

    @PostMapping("/redemptions/{id}/reject")
    public ResponseEntity<?> reject(@PathVariable Long id) {
        service.reject(id);
        return ResponseEntity.ok().build();
    }
}
