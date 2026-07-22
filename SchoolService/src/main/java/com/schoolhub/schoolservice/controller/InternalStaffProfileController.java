package com.schoolhub.schoolservice.controller;

import com.schoolhub.schoolservice.service.InternalStaffProfileService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Internal endpoints called by TenantService when a staff signup is approved.
 * Secured by whitelist in SecurityConfig (/internal/**) — these are NOT
 * exposed through the gateway.
 */
@RestController
@RequestMapping("/internal/staff-profiles")
public class InternalStaffProfileController {

    private final InternalStaffProfileService service;

    public InternalStaffProfileController(InternalStaffProfileService service) {
        this.service = service;
    }

    @PostMapping("/teacher")
    public ResponseEntity<?> createTeacher(@RequestBody Map<String, String> body) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.createTeacher(body));
    }

    @PostMapping("/bursar")
    public ResponseEntity<?> createBursar(@RequestBody Map<String, String> body) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.createBursar(body));
    }

    @PostMapping("/librarian")
    public ResponseEntity<?> createLibrarian(@RequestBody Map<String, String> body) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.createLibrarian(body));
    }
}
