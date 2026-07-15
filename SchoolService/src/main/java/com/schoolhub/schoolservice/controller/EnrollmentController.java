package com.schoolhub.schoolservice.controller;

import com.schoolhub.schoolservice.dto.Requests.EnrollmentCloseReq;
import com.schoolhub.schoolservice.dto.Requests.EnrollmentReq;
import com.schoolhub.schoolservice.service.EnrollmentService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/enrollments")
public class EnrollmentController {

    private final EnrollmentService service;

    public EnrollmentController(EnrollmentService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER','BURSAR')")
    public ResponseEntity<?> listForStudent(@RequestParam Long studentId) {
        return ResponseEntity.ok(service.listForStudent(studentId));
    }

    @GetMapping("/by-cohort/{cohortId}")
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER')")
    public ResponseEntity<?> listForCohort(@PathVariable Long cohortId) {
        return ResponseEntity.ok(service.listForCohort(cohortId));
    }

    // A teacher enrolls a student into their own cohort; admin can enrol into any.
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER')")
    public ResponseEntity<?> create(@Valid @RequestBody EnrollmentReq req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(req));
    }

    @PostMapping("/{id}/close")
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER')")
    public ResponseEntity<?> close(@PathVariable Long id, @Valid @RequestBody EnrollmentCloseReq req) {
        return ResponseEntity.ok(service.close(id, req));
    }
}
