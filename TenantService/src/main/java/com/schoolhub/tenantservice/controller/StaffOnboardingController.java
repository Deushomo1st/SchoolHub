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

    // Public: a staff member signs up with the school's code.
    @PostMapping("/staff-signup")
    public ResponseEntity<?> staffSignup(@Valid @RequestBody StaffSignupRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(staff.staffSignup(req));
    }

    // Admin: view / (re)issue this school's staff code.
    @GetMapping("/staff-code")
    @PreAuthorize("hasAnyRole('ADMIN','PRINCIPAL')")
    public ResponseEntity<?> currentCode(Authentication auth) {
        String code = staff.currentStaffCode((Long) auth.getPrincipal());
        return ResponseEntity.ok(Map.of("staffCode", code == null ? "" : code));
    }

    @PostMapping("/staff-code")
    @PreAuthorize("hasAnyRole('ADMIN','PRINCIPAL')")
    public ResponseEntity<?> issueCode(Authentication auth) {
        return ResponseEntity.ok(Map.of("staffCode", staff.generateStaffCode((Long) auth.getPrincipal())));
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

    @DeleteMapping("/staff/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','PRINCIPAL')")
    public ResponseEntity<?> removeStaff(@PathVariable Long id, Authentication auth) {
        staff.deleteStaff((Long) auth.getPrincipal(), id);
        return ResponseEntity.ok().build();
    }
}
