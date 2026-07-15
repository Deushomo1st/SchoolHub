package com.schoolhub.authservice.controller;

import com.schoolhub.authservice.dto.*;
import com.schoolhub.authservice.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest req) {
        return ResponseEntity.ok(authService.login(req));
    }

    @PostMapping("/refresh")
    public ResponseEntity<?> refresh(@Valid @RequestBody RefreshRequest req) {
        return ResponseEntity.ok(authService.refresh(req.getRefreshToken()));
    }

    @PostMapping("/logout/refresh")
    public ResponseEntity<?> logoutRefresh(@RequestBody Map<String, String> body) {
        String token = body.get("refreshToken");
        if (token != null) authService.logoutRefresh(token);
        return ResponseEntity.ok(Map.of("message", "Logged out"));
    }

    @GetMapping("/me")
    public ResponseEntity<?> me(Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        return ResponseEntity.ok(authService.getCurrentUser(userId));
    }

    // Re-mint a token from a different role assignment the caller holds (e.g. teacher at
    // one school, guardian at another - or a second role within the same school).
    @PostMapping("/switch-context")
    public ResponseEntity<?> switchContext(@Valid @RequestBody SwitchContextRequest req, Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        return ResponseEntity.ok(authService.switchContext(userId, req.getRoleAssignmentId()));
    }

    // Any signed-in user can change their own password (everyone starts on an admin-set temp one).
    @PostMapping("/change-password")
    public ResponseEntity<?> changePassword(@Valid @RequestBody ChangePasswordRequest req, Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        authService.changePassword(userId, req.getCurrentPassword(), req.getNewPassword());
        return ResponseEntity.ok(Map.of("message", "Password updated"));
    }

    // Check if an email is already registered (public — used by login/signup forms).
    @PostMapping("/check-email")
    public ResponseEntity<?> checkEmail(@Valid @RequestBody CheckEmailRequest req) {
        boolean exists = authService.checkEmail(req.getEmail());
        return ResponseEntity.ok(Map.of("exists", exists, "email", req.getEmail().trim().toLowerCase()));
    }

    // Sign in with a Google ID token. If a SchoolHub account with the Google email
    // already exists, the user is logged in. Otherwise, they're told to sign up first.
    @PostMapping("/google")
    public ResponseEntity<?> googleLogin(@Valid @RequestBody GoogleLoginRequest req) {
        return ResponseEntity.ok(authService.loginWithGoogle(req.getIdToken()));
    }

    // School admin/principal resets one of their own school's users (lockout / forgot-password recovery).
    @PostMapping("/admin/reset-password")
    @PreAuthorize("hasAnyRole('ADMIN','PRINCIPAL')")
    public ResponseEntity<?> adminReset(@Valid @RequestBody AdminResetRequest req, Authentication authentication) {
        Long callerId = (Long) authentication.getPrincipal();
        authService.adminResetPassword(callerId, req.getEmail(), req.getNewPassword());
        return ResponseEntity.ok(Map.of("message", "Password reset. Share the new temp password with the user."));
    }
}
