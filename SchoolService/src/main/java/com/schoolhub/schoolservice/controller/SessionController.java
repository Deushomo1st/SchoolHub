package com.schoolhub.schoolservice.controller;

import com.schoolhub.schoolservice.dto.Requests.SessionAttendanceReq;
import com.schoolhub.schoolservice.dto.Requests.SessionRescheduleReq;
import com.schoolhub.schoolservice.dto.Requests.SessionReq;
import com.schoolhub.schoolservice.service.SessionAttendanceService;
import com.schoolhub.schoolservice.service.SessionService;
import com.schoolhub.schoolservice.service.WorkflowService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/sessions")
public class SessionController {

    private final SessionService service;
    private final SessionAttendanceService attendance;
    private final WorkflowService workflow;

    public SessionController(SessionService service, SessionAttendanceService attendance, WorkflowService workflow) {
        this.service = service;
        this.attendance = attendance;
        this.workflow = workflow;
    }

    @GetMapping
    public ResponseEntity<?> list(@RequestParam Long cohortOfferingId) {
        return ResponseEntity.ok(service.listForCohortOffering(cohortOfferingId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> get(@PathVariable Long id) { return ResponseEntity.ok(service.get(id)); }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER')")
    public ResponseEntity<?> create(@Valid @RequestBody SessionReq req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(req));
    }

    @PostMapping("/{id}/reschedule")
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER')")
    public ResponseEntity<?> reschedule(@PathVariable Long id, @Valid @RequestBody SessionRescheduleReq req) {
        return ResponseEntity.ok(service.reschedule(id, req.startAt(), req.endAt()));
    }

    // Lightweight - status flag flips immediately. The returned workflow_request's id is the
    // comment thread (POST/GET /api/v1/workflow-requests/{id}/protests), same mechanism as
    // everywhere else, not a bespoke session-comment endpoint.
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER')")
    public ResponseEntity<?> cancel(@PathVariable Long id, @RequestParam(required = false) String reason) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(workflow.initiateSessionCancel(id, reason));
    }

    // ---- Per-session attendance ----

    @GetMapping("/{id}/attendance")
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER')")
    public ResponseEntity<?> listAttendance(@PathVariable Long id) {
        return ResponseEntity.ok(attendance.listForSession(id));
    }

    @PostMapping("/{id}/attendance")
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER')")
    public ResponseEntity<?> markAttendance(@PathVariable Long id, @Valid @RequestBody SessionAttendanceReq req) {
        return ResponseEntity.ok(attendance.mark(id, req));
    }
}
