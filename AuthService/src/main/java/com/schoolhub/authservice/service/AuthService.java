package com.schoolhub.authservice.service;

import com.schoolhub.authservice.dto.*;
import com.schoolhub.authservice.model.*;
import com.schoolhub.authservice.repository.*;
import com.schoolhub.authservice.util.JwtUtil;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

@Service
public class AuthService {

    private final AppUserRepository userRepo;
    private final RoleRepository roleRepo;
    private final TenantRepository tenantRepo;
    private final TokenBlacklistRepository blacklistRepo;
    private final RoleAssignmentRepository roleAssignmentRepo;
    private final JwtUtil jwtUtil;
    private final BCryptPasswordEncoder passwordEncoder;
    private final LoginRateLimiter rateLimiter;
    private final RestTemplate restTemplate;

    public AuthService(AppUserRepository userRepo, RoleRepository roleRepo, TenantRepository tenantRepo,
                       TokenBlacklistRepository blacklistRepo, RoleAssignmentRepository roleAssignmentRepo,
                       JwtUtil jwtUtil, BCryptPasswordEncoder passwordEncoder,
                       LoginRateLimiter rateLimiter, RestTemplate restTemplate) {
        this.userRepo = userRepo;
        this.roleRepo = roleRepo;
        this.tenantRepo = tenantRepo;
        this.blacklistRepo = blacklistRepo;
        this.roleAssignmentRepo = roleAssignmentRepo;
        this.jwtUtil = jwtUtil;
        this.passwordEncoder = passwordEncoder;
        this.rateLimiter = rateLimiter;
        this.restTemplate = restTemplate;
    }

    // ---------- Bootstrap (first PLATFORM_OWNER) ----------

    public boolean needsBootstrap() {
        return userRepo.count() == 0;
    }

    @Transactional
    public TokenResponse bootstrap(BootstrapRequest req) {
        if (!needsBootstrap()) {
            throw new AccessDeniedException("Bootstrap is only allowed on a fresh database");
        }
        Role owner = roleRepo.findByName("PLATFORM_OWNER")
                .orElseThrow(() -> new IllegalStateException("PLATFORM_OWNER role missing - DDL was not applied"));
        AppUser user = new AppUser();
        user.setEmail(req.getEmail());
        user.setPasswordHash(passwordEncoder.encode(req.getPassword()));
        user.setFirstName(req.getFirstName());
        user.setLastName(req.getLastName());
        user.setPhone(req.getPhone());
        user.setRoleId(owner.getId());
        user.setTenantId(null);            // platform owner is not tied to any school
        user.setAccountStatus("active");
        user = userRepo.save(user);
        ResolvedContext ctx = new ResolvedContext(owner.getName(), null, null);
        return issueTokenPair(user, ctx);
    }

    // ---------- Authentication ----------

    @Transactional
    public TokenResponse login(LoginRequest req) {
        String identifier = req.getEmail().trim();   // may be an email OR a username
        long locked = rateLimiter.lockedSeconds(identifier);
        if (locked > 0) {
            throw new LoginThrottledException(
                    "Too many failed attempts. Try again in about " + ((locked / 60) + 1) + " minute(s).");
        }
        AppUser user = userRepo.findByEmailIgnoreCase(identifier)
                .or(() -> userRepo.findByUsernameIgnoreCase(identifier))
                .orElse(null);
        if (user == null || !passwordEncoder.matches(req.getPassword(), user.getPasswordHash())) {
            rateLimiter.recordFailure(identifier);
            throw new BadCredentialsException("Wrong username/email or password");
        }
        rateLimiter.recordSuccess(identifier);
        rejectInactiveAccount(user);
        ResolvedContext ctx = resolveLoginContext(user);
        // Platform owners may only enter through the padlock (backdoor) path.
        // Same generic message as a bad password so the account's nature isn't leaked.
        if ("PLATFORM_OWNER".equals(ctx.roleName()) && !req.isLock()) {
            throw new BadCredentialsException("Wrong username/email or password");
        }
        user.setLastLoginAt(LocalDateTime.now());
        userRepo.save(user);
        return issueTokenPair(user, ctx);
    }

    /** Re-mint a token from a different role assignment the caller holds (multi-role/multi-school switch). */
    @Transactional
    public TokenResponse switchContext(Long callerUserId, Long roleAssignmentId) {
        AppUser user = userRepo.findById(callerUserId)
                .orElseThrow(() -> new EntityNotFoundException("User not found"));
        RoleAssignment ra = roleAssignmentRepo.findById(roleAssignmentId)
                .orElseThrow(() -> new EntityNotFoundException("Role assignment not found"));
        if (!ra.getAppUserId().equals(callerUserId)) {
            throw new AccessDeniedException("That role assignment does not belong to you");
        }
        if (!"active".equals(ra.getStatus())) {
            throw new BadCredentialsException("That role is not active");
        }
        return issueTokenPair(user, contextFromAssignment(ra));
    }

    @Transactional
    public TokenResponse refresh(String refreshTokenStr) {
        Claims claims;
        try {
            claims = jwtUtil.parseToken(refreshTokenStr);
        } catch (JwtException e) {
            throw new BadCredentialsException("Invalid refresh token");
        }
        if (!JwtUtil.TYPE_REFRESH.equals(jwtUtil.getType(claims))) {
            throw new BadCredentialsException("Token is not a refresh token");
        }
        String jti = jwtUtil.getJti(claims);
        if (jti != null && blacklistRepo.existsByTokenJti(jti)) {
            throw new BadCredentialsException("Refresh token has been revoked");
        }
        AppUser user = userRepo.findById(jwtUtil.getUserId(claims))
                .orElseThrow(() -> new BadCredentialsException("User not found"));
        rejectInactiveAccount(user);
        blacklistRefreshToken(claims);     // rotate
        return issueTokenPair(user, resolveLoginContext(user));
    }

    @Transactional
    public void logoutRefresh(String refreshTokenStr) {
        try {
            Claims claims = jwtUtil.parseToken(refreshTokenStr);
            if (JwtUtil.TYPE_REFRESH.equals(jwtUtil.getType(claims))) {
                blacklistRefreshToken(claims);
            }
        } catch (JwtException ignored) {
            // Idempotent: a malformed/expired token is treated as already-logged-out.
        }
    }

    public UserDto getCurrentUser(Long userId) {
        AppUser u = userRepo.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("User not found"));
        String rawRole = roleNameOf(u);
        String displayRole = ROLE_DISPLAY.getOrDefault(rawRole, rawRole.replace('_', ' '));
        UserDto dto = toDto(u, displayRole, rawRole);
        dto.setRoleAssignments(getRoleAssignmentDtos(userId));
        return dto;
    }

    /** Set (or clear, when null) the caller's own profile picture. Expects a base64 image data-URL. */
    @Transactional
    public UserDto setAvatar(Long userId, String avatar) {
        AppUser user = userRepo.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("User not found"));
        if (avatar != null) {
            if (!avatar.startsWith("data:image/")) {
                throw new IllegalArgumentException("Avatar must be an image data URL");
            }
            if (avatar.length() > 3_000_000) {   // ~2 MB of base64 — cropper should send far less
                throw new IllegalArgumentException("Image is too large; crop or pick a smaller one");
            }
        }
        user.setAvatar(avatar);
        userRepo.save(user);
        return getCurrentUser(userId);
    }

    @Transactional
    public void changePassword(Long userId, String currentPassword, String newPassword) {
        AppUser user = userRepo.findById(userId)
                .orElseThrow(() -> new BadCredentialsException("User not found"));
        if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw new BadCredentialsException("Current password is incorrect");
        }
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepo.save(user);
    }

    /** A school admin/principal resets one of THEIR OWN school's users to a new temp password. */
    @Transactional
    public void adminResetPassword(Long callerUserId, String email, String newPassword) {
        AppUser caller = userRepo.findById(callerUserId)
                .orElseThrow(() -> new EntityNotFoundException("User not found"));
        if (caller.getTenantId() == null) {
            throw new AccessDeniedException("Only school administrators can reset their users' passwords");
        }
        AppUser target = userRepo.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new EntityNotFoundException("No user with that email"));
        if (target.getTenantId() == null || !target.getTenantId().equals(caller.getTenantId())) {
            throw new AccessDeniedException("You can only reset passwords for users in your own school");
        }
        target.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepo.save(target);
        rateLimiter.recordSuccess(email);   // also clears any lockout so they can sign in
    }

    // ---------- Email existence check ----------

    public boolean checkEmail(String email) {
        return userRepo.existsByEmailIgnoreCase(email.trim().toLowerCase());
    }

    // ---------- Google sign-in ----------

    @Transactional
    public TokenResponse loginWithGoogle(String idToken) {
        // Verify the token with Google
        GoogleUser googleUser = verifyGoogleToken(idToken);
        String email = googleUser.email().toLowerCase();

        AppUser user = userRepo.findByEmailIgnoreCase(email).orElse(null);
        if (user == null) {
            throw new BadCredentialsException(
                    "No SchoolHub account found for " + email + ". Sign up first, then sign in with Google.");
        }
        rejectInactiveAccount(user);
        ResolvedContext ctx = resolveLoginContext(user);
        // Platform owners never sign in via Google — pretend the account doesn't exist here.
        if ("PLATFORM_OWNER".equals(ctx.roleName())) {
            throw new BadCredentialsException(
                    "No SchoolHub account found for " + email + ". Sign up first, then sign in with Google.");
        }
        user.setLastLoginAt(LocalDateTime.now());
        userRepo.save(user);
        return issueTokenPair(user, ctx);
    }

    // ---------- Helpers ----------

    private void blacklistRefreshToken(Claims claims) {
        String jti = jwtUtil.getJti(claims);
        if (jti == null || blacklistRepo.existsByTokenJti(jti)) return;
        TokenBlacklist row = new TokenBlacklist();
        row.setUserId(jwtUtil.getUserId(claims));
        row.setTokenJti(jti);
        row.setExpiresAt(LocalDateTime.ofInstant(jwtUtil.getExpiry(claims), ZoneId.systemDefault()));
        blacklistRepo.save(row);
    }

    private TokenResponse issueTokenPair(AppUser user, ResolvedContext ctx) {
        String access = jwtUtil.generateAccessToken(
                user.getId(), user.getEmail(), ctx.roleName(), ctx.tenantId(), ctx.tenantSchema());
        String refresh = jwtUtil.generateRefreshToken(
                user.getId(), user.getEmail(), ctx.roleName(), ctx.tenantId(), ctx.tenantSchema());
        String displayRole = ROLE_DISPLAY.getOrDefault(ctx.roleName(), ctx.roleName().replace('_', ' '));
        UserDto dto = toDto(user, displayRole, ctx.roleName());
        dto.setRoleAssignments(getRoleAssignmentDtos(user.getId()));
        return new TokenResponse(access, refresh, jwtUtil.getAccessExpirySeconds(), dto);
    }

    /**
     * The role+tenant a token should be minted from: the user's default role_assignment
     * if one exists, falling back to the legacy single role_id/tenant_id on AppUser for
     * users who haven't been migrated onto role_assignment rows yet.
     */
    private ResolvedContext resolveLoginContext(AppUser user) {
        return roleAssignmentRepo.findByAppUserIdAndIsDefaultTrueAndStatus(user.getId(), "active")
                .map(this::contextFromAssignment)
                .orElseGet(() -> new ResolvedContext(roleNameOf(user), user.getTenantId(), resolveTenantSchemaById(user.getTenantId())));
    }

    private ResolvedContext contextFromAssignment(RoleAssignment ra) {
        Role role = roleRepo.findById(ra.getRoleId())
                .orElseThrow(() -> new IllegalStateException("Role missing for assignment " + ra.getId()));
        return new ResolvedContext(role.getName(), ra.getTenantId(), resolveTenantSchemaById(ra.getTenantId()));
    }

    /** PLATFORM_OWNER has no tenant; everyone else carries their school's schema in the token. */
    private String resolveTenantSchemaById(Long tenantId) {
        if (tenantId == null) return null;
        Tenant t = tenantRepo.findById(tenantId)
                .orElseThrow(() -> new IllegalStateException("Missing tenant " + tenantId));
        if (!"active".equals(t.getStatus())) {
            String msg = switch (t.getStatus()) {
                case "pending"  -> "Your school has not been approved yet.";
                case "suspended" -> "This school has been suspended or is inactive.";
                default           -> "This school is " + t.getStatus() + ". Contact SchoolHub support.";
            };
            throw new BadCredentialsException(msg);
        }
        return t.getSchemaName();
    }

    /** The role+tenant a token is minted from - either a role_assignment row, or the legacy AppUser fields. */
    private record ResolvedContext(String roleName, Long tenantId, String tenantSchema) {}

    private void rejectInactiveAccount(AppUser user) {
        switch (user.getAccountStatus()) {
            case "suspended" -> throw new BadCredentialsException("Account suspended");
            case "disabled"  -> throw new BadCredentialsException("Account disabled");
            case "pending"   -> throw new BadCredentialsException("Account pending activation");
            default -> { /* active / invited can sign in */ }
        }
    }

    private String roleNameOf(AppUser user) {
        return roleRepo.findById(user.getRoleId())
                .map(Role::getName)
                .orElseThrow(() -> new IllegalStateException("Role missing for user " + user.getId()));
    }

    private UserDto toDto(AppUser u, String displayRole, String rawRole) {
        UserDto dto = new UserDto();
        dto.setId(u.getId());
        dto.setEmail(u.getEmail());
        dto.setUsername(u.getUsername());
        dto.setFirstName(u.getFirstName());
        dto.setLastName(u.getLastName());
        dto.setPhone(u.getPhone());
        dto.setRole(displayRole);
        dto.setRoleCode(rawRole);
        dto.setTenantId(u.getTenantId());
        dto.setAccountStatus(u.getAccountStatus());
        dto.setAvatar(u.getAvatar());
        dto.setLastLoginAt(u.getLastLoginAt());
        dto.setCreatedAt(u.getCreatedAt());
        return dto;
    }

    private static final java.util.Map<String, String> ROLE_DISPLAY = java.util.Map.of(
        "PLATFORM_OWNER", "Platform Owner",
        "ADMIN",          "School Admin",
        "PRINCIPAL",      "Principal",
        "TEACHER",        "Teacher",
        "STUDENT",        "Student",
        "PARENT",         "Parent",
        "BURSAR",         "Bursar",
        "MODERATOR",      "Moderator"
    );

    private List<RoleAssignmentDto> getRoleAssignmentDtos(Long userId) {
        return roleAssignmentRepo.findByAppUserIdAndStatus(userId, "active").stream()
                .map(ra -> {
                    Role role = roleRepo.findById(ra.getRoleId()).orElse(null);
                    String roleName = role != null ? role.getName() : "UNKNOWN";
                    String displayName = ROLE_DISPLAY.getOrDefault(roleName, roleName.replace('_', ' '));
                    String tenantName = null;
                    if (ra.getTenantId() != null) {
                        tenantName = tenantRepo.findById(ra.getTenantId())
                                .map(Tenant::getName).orElse(null);
                    }
                    return new RoleAssignmentDto(ra.getId(), displayName,
                            ra.getTenantId(), tenantName, ra.isDefault());
                })
                .toList();
    }

    // Not a secret — the same ID is public in login.html. It pins token audience.
    private static final String GOOGLE_CLIENT_ID =
            "756001532243-io5srs96js86euebruv1f0jsjnt2u63j.apps.googleusercontent.com";

    private GoogleUser verifyGoogleToken(String idToken) {
        try {
            String url = "https://www.googleapis.com/oauth2/v3/tokeninfo?id_token=" + idToken;
            ResponseEntity<Map> resp = restTemplate.getForEntity(url, Map.class);
            Map<String, Object> body = resp.getBody();
            if (body == null || body.containsKey("error_description")) {
                String err = body != null ? (String) body.get("error_description") : "Invalid token";
                throw new BadCredentialsException("Google sign-in failed: " + err);
            }
            // The token must have been issued to OUR app, not just any Google client.
            if (!GOOGLE_CLIENT_ID.equals(body.get("aud"))) {
                throw new BadCredentialsException("Google sign-in failed: token was not issued for SchoolHub");
            }
            String email = (String) body.get("email");
            String name = (String) body.get("name");
            if (email == null) {
                throw new BadCredentialsException("Google sign-in failed: no email in token");
            }
            return new GoogleUser(email, name != null ? name : email);
        } catch (BadCredentialsException e) {
            throw e;
        } catch (Exception e) {
            throw new BadCredentialsException("Google sign-in verification failed: " + e.getMessage());
        }
    }

    private record GoogleUser(String email, String name) {}

    // Local exception types - mapped to 401/409 by the global handler.
    public static class BadCredentialsException extends RuntimeException {
        public BadCredentialsException(String msg) { super(msg); }
    }
    public static class ConflictException extends RuntimeException {
        public ConflictException(String msg) { super(msg); }
    }
    public static class LoginThrottledException extends RuntimeException {
        public LoginThrottledException(String msg) { super(msg); }
    }
}
