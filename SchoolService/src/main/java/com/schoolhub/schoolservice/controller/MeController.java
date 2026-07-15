package com.schoolhub.schoolservice.controller;

import com.schoolhub.schoolservice.service.FeeService;
import com.schoolhub.schoolservice.service.PerspectiveService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Role-specific dashboard bundles for the logged-in user. */
@RestController
@RequestMapping("/api/v1/me")
public class MeController {

    private final PerspectiveService perspectives;
    private final FeeService fees;

    public MeController(PerspectiveService perspectives, FeeService fees) {
        this.perspectives = perspectives;
        this.fees = fees;
    }

    @GetMapping("/school")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> school() {
        return ResponseEntity.ok(perspectives.schoolSummary());
    }

    @GetMapping("/teacher")
    @PreAuthorize("hasRole('TEACHER')")
    public ResponseEntity<?> teacher(Authentication auth) {
        return ResponseEntity.ok(perspectives.teacherDashboard((Long) auth.getPrincipal()));
    }

    @GetMapping("/student")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<?> student(Authentication auth) {
        return ResponseEntity.ok(perspectives.studentDashboard((Long) auth.getPrincipal()));
    }

    @GetMapping("/guardian")
    @PreAuthorize("hasRole('PARENT')")
    public ResponseEntity<?> guardian(Authentication auth) {
        return ResponseEntity.ok(perspectives.guardianDashboard((Long) auth.getPrincipal()));
    }

    // "For You": everything the signed-in student (or a guardian's children) owes. Ownership is
    // resolved server-side from the token - the caller never passes a student id.
    @GetMapping("/foryou")
    @PreAuthorize("hasAnyRole('STUDENT','PARENT')")
    public ResponseEntity<?> forYou() {
        return ResponseEntity.ok(fees.forYou());
    }
}
