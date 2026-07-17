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
    private final com.schoolhub.authservice.service.PasswordResetService passwordReset;

    public AuthController(AuthService authService,
                          com.schoolhub.authservice.service.PasswordResetService passwordReset) {
        this.authService = authService;
        this.passwordReset = passwordReset;
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

    // Set or clear the caller's own profile picture (base64 image data-URL; null clears it).
    @PutMapping("/me/avatar")
    public ResponseEntity<?> setAvatar(@RequestBody Map<String, String> body, Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        return ResponseEntity.ok(authService.setAvatar(userId, body.get("avatar")));
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

    // Forgot password (public). Always the same neutral answer — no account enumeration.
    @PostMapping("/forgot-password")
    public ResponseEntity<?> forgotPassword(@RequestBody Map<String, String> body) {
        passwordReset.requestReset(body.get("email"));
        return ResponseEntity.ok(Map.of("message", "If that email has an account, a reset link is on its way."));
    }

    // Complete the reset with the emailed token (public).
    @PostMapping("/reset-password")
    public ResponseEntity<?> resetPassword(@RequestBody Map<String, String> body) {
        passwordReset.reset(body.get("token"), body.get("newPassword"));
        return ResponseEntity.ok(Map.of("message", "Password updated — log in with the new one."));
    }

    // Check if an email is already registered (public — used by login/signup forms).
    @PostMapping("/check-email")
    public ResponseEntity<?> checkEmail(@Valid @RequestBody CheckEmailRequest req) {
        boolean exists = authService.checkEmail(req.getEmail());
        return ResponseEntity.ok(Map.of("exists", exists, "email", req.getEmail().trim().toLowerCase()));
    }

    // Sign in with Google. Three paths:
    //  1. idToken  — standard One Tap / popup ID token (verified against Google)
    //  2. code     — OAuth authorization code (exchanged server-side for user info)
    //  3. lock=true + email — local dev backdoor: logs in by email, no Google verification
    @PostMapping("/google")
    public ResponseEntity<?> googleLogin(@RequestBody GoogleLoginRequest req) {
        // Lock backdoor: bypass Google entirely (local dev/testing for ALL roles)
        if (Boolean.TRUE.equals(req.getLock()) && req.getEmail() != null) {
            return ResponseEntity.ok(authService.loginWithGoogleBypass(req.getEmail().trim()));
        }
        if (req.getIdToken() != null && !req.getIdToken().isBlank()) {
            return ResponseEntity.ok(authService.loginWithGoogle(req.getIdToken()));
        }
        if (req.getCode() != null && !req.getCode().isBlank()) {
            return ResponseEntity.ok(authService.loginWithGoogleCode(req.getCode()));
        }
        throw new IllegalArgumentException("idToken, code, or lock+email required");
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
