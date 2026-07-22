package com.schoolhub.tenantservice.controller;

import com.schoolhub.tenantservice.dto.StaffSignupRequest;
import com.schoolhub.tenantservice.service.StaffOnboardingService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** Staff self-onboarding: public sign-up by code + school-admin code issue and approvals. */
@RestController
@RequestMapping("/api/v1/tenants")
public class StaffOnboardingController {

    private final StaffOnboardingService staff;

    public StaffOnboardingController(StaffOnboardingService staff) {
        this.staff = staff;
    }

    // Public: a staff member signs up with the school's per-role code.
    @PostMapping("/staff-signup")
    public ResponseEntity<?> staffSignup(@Valid @RequestBody StaffSignupRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(staff.staffSignup(req));
    }

    // Admin: get all per-role staff codes for this school.
    @GetMapping("/staff-codes")
    @PreAuthorize("hasAnyRole('ADMIN','PRINCIPAL')")
    public ResponseEntity<?> allCodes(Authentication auth) {
        return ResponseEntity.ok(staff.allStaffCodes((Long) auth.getPrincipal()));
    }

    // Admin: generate individual per-role codes.
    @PostMapping("/staff-codes/teacher")
    @PreAuthorize("hasAnyRole('ADMIN','PRINCIPAL')")
    public ResponseEntity<?> generateTeacherCode(Authentication auth) {
        return ResponseEntity.ok(Map.of("teacherCode", staff.generateTeacherCode((Long) auth.getPrincipal())));
    }

    @PostMapping("/staff-codes/bursar")
    @PreAuthorize("hasAnyRole('ADMIN','PRINCIPAL')")
    public ResponseEntity<?> generateBursarCode(Authentication auth) {
        return ResponseEntity.ok(Map.of("bursarCode", staff.generateBursarCode((Long) auth.getPrincipal())));
    }

    @PostMapping("/staff-codes/librarian")
    @PreAuthorize("hasAnyRole('ADMIN','PRINCIPAL')")
    public ResponseEntity<?> generateLibrarianCode(Authentication auth) {
        return ResponseEntity.ok(Map.of("librarianCode", staff.generateLibrarianCode((Long) auth.getPrincipal())));
    }

    // Admin: review pending staff and approve / reject.
    @GetMapping("/pending-staff")
    @PreAuthorize("hasAnyRole('ADMIN','PRINCIPAL')")
    public ResponseEntity<?> pending(Authentication auth) {
        return ResponseEntity.ok(staff.listPendingStaff((Long) auth.getPrincipal()));
    }

    @PostMapping("/pending-staff/{id}/approve")
    @PreAuthorize("hasAnyRole('ADMIN','PRINCIPAL')")
    public ResponseEntity<?> approve(@PathVariable Long id, Authentication auth) {
        return ResponseEntity.ok(staff.approveStaff((Long) auth.getPrincipal(), id));
    }

    @PostMapping("/pending-staff/{id}/reject")
    @PreAuthorize("hasAnyRole('ADMIN','PRINCIPAL')")
    public ResponseEntity<?> reject(@PathVariable Long id, Authentication auth) {
        staff.rejectStaff((Long) auth.getPrincipal(), id);
        return ResponseEntity.ok().build();
    }

    // Admin: manage active staff (suspend / re-activate / remove).
    @GetMapping("/staff")
    @PreAuthorize("hasAnyRole('ADMIN','PRINCIPAL')")
    public ResponseEntity<?> listStaff(Authentication auth) {
        return ResponseEntity.ok(staff.listStaff((Long) auth.getPrincipal()));
    }

    @PostMapping("/staff/{id}/suspend")
    @PreAuthorize("hasAnyRole('ADMIN','PRINCIPAL')")
    public ResponseEntity<?> suspendStaff(@PathVariable Long id, Authentication auth) {
        staff.setStaffStatus((Long) auth.getPrincipal(), id, "suspended");
        return ResponseEntity.ok().build();
    }

    @PostMapping("/staff/{id}/activate")
    @PreAuthorize("hasAnyRole('ADMIN','PRINCIPAL')")
    public ResponseEntity<?> activateStaff(@PathVariable Long id, Authentication auth) {
        staff.setStaffStatus((Long) auth.getPrincipal(), id, "active");
        return ResponseEntity.ok().build();
    }

    @PutMapping("/staff/{id}/title")
    @PreAuthorize("hasAnyRole('ADMIN','PRINCIPAL')")
    public ResponseEntity<?> setStaffTitle(@PathVariable Long id, @RequestBody Map<String, String> body,
                                           Authentication auth) {
        staff.setStaffTitle((Long) auth.getPrincipal(), id, body.get("title"));
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/staff/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','PRINCIPAL')")
    public ResponseEntity<?> removeStaff(@PathVariable Long id, Authentication auth) {
        staff.deleteStaff((Long) auth.getPrincipal(), id);
        return ResponseEntity.ok().build();
    }
}
