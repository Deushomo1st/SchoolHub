package com.schoolhub.tenantservice.service;

import com.schoolhub.tenantservice.dto.StaffSignupRequest;
import com.schoolhub.tenantservice.model.*;
import com.schoolhub.tenantservice.repository.*;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.*;

/**
 * Staff self-onboarding: a school issues a code, staff sign up with it into that
 * school as PENDING, and the school admin approves before they can sign in.
 * "the administration still covers it" - every code-signup waits for admin approval.
 */
@Service
public class StaffOnboardingService {

    // Codes a person reads/types: no I, L, O, 0, 1 to avoid confusion.
    private static final char[] CODE_ALPHABET = "ABCDEFGHJKMNPQRSTVWXYZ23456789".toCharArray();
    private static final Set<String> SELF_SIGNUP_ROLES = Set.of("TEACHER", "BURSAR");
    private static final SecureRandom RANDOM = new SecureRandom();

    private final TenantRepository tenantRepo;
    private final AppUserRepository userRepo;
    private final RoleRepository roleRepo;
    private final BCryptPasswordEncoder encoder;
    private final AuditService audit;

    public StaffOnboardingService(TenantRepository tenantRepo, AppUserRepository userRepo, RoleRepository roleRepo,
                                  BCryptPasswordEncoder encoder, AuditService audit) {
        this.tenantRepo = tenantRepo;
        this.userRepo = userRepo;
        this.roleRepo = roleRepo;
        this.encoder = encoder;
        this.audit = audit;
    }

    // ---- Admin: issue / view the school's staff code ----

    public String currentStaffCode(Long callerUserId) {
        return callerTenant(callerUserId).getStaffCode();
    }

    @Transactional
    public String generateStaffCode(Long callerUserId) {
        Tenant t = callerTenant(callerUserId);
        String code;
        do { code = randomCode(); } while (tenantRepo.existsByStaffCode(code));
        t.setStaffCode(code);
        tenantRepo.save(t);
        audit.record(t.getId(), callerUserId, "STAFF_CODE_ISSUED", t.getName());
        return code;
    }

    // ---- Public: staff signs up with the code ----

    @Transactional
    public Map<String, Object> staffSignup(StaffSignupRequest req) {
        String roleName = (req.role() == null || req.role().isBlank()) ? "TEACHER" : req.role().toUpperCase();
        if (!SELF_SIGNUP_ROLES.contains(roleName)) {
            throw new IllegalArgumentException("Staff sign-up is only for TEACHER or BURSAR");
        }
        Tenant tenant = tenantRepo.findByStaffCode(req.code().trim())
                .orElseThrow(() -> new IllegalArgumentException("That staff code is not valid"));
        if (!"active".equals(tenant.getStatus())) {
            throw new IllegalArgumentException("This school is not active, so sign-up is closed");
        }
        if (userRepo.existsByEmailIgnoreCase(req.email())) {
            throw new TenantService.ConflictException("That email is already registered");
        }
        Role role = roleRepo.findByName(roleName)
                .orElseThrow(() -> new IllegalStateException("Role missing: " + roleName));

        AppUser u = new AppUser();
        u.setEmail(req.email());
        u.setUsername(uniqueUsername(req.firstName(), req.lastName()));
        u.setPasswordHash(encoder.encode(req.password()));
        u.setFirstName(req.firstName());
        u.setLastName(req.lastName());
        u.setRoleId(role.getId());
        u.setTenantId(tenant.getId());
        u.setAccountStatus("pending");     // waits for the school admin
        userRepo.save(u);
        audit.record(tenant.getId(), null, "STAFF_SIGNUP_REQUESTED", roleName + ": " + req.email());

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("school", tenant.getName());
        m.put("status", "pending");
        m.put("message", "Request submitted. Your " + tenant.getName()
                + " administrator must approve it before you can sign in.");
        return m;
    }

    // ---- Admin: review / approve / reject pending staff ----

    public List<Map<String, Object>> listPendingStaff(Long callerUserId) {
        Tenant t = callerTenant(callerUserId);
        Map<Long, String> roleNames = roleRepo.findAll().stream()
                .collect(java.util.stream.Collectors.toMap(Role::getId, Role::getName));
        List<Map<String, Object>> out = new ArrayList<>();
        for (AppUser u : userRepo.findByTenantIdAndAccountStatusOrderByCreatedAtDesc(t.getId(), "pending")) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", u.getId());
            row.put("name", u.getFirstName() + " " + u.getLastName());
            row.put("email", u.getEmail());
            row.put("username", u.getUsername());
            row.put("role", roleNames.getOrDefault(u.getRoleId(), "?"));
            out.add(row);
        }
        return out;
    }

    @Transactional
    public Map<String, Object> approveStaff(Long callerUserId, Long staffUserId) {
        AppUser staff = pendingStaffInCallerSchool(callerUserId, staffUserId);
        staff.setAccountStatus("active");
        userRepo.save(staff);
        audit.record(staff.getTenantId(), callerUserId, "STAFF_APPROVED", staff.getEmail());
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", staff.getId());
        m.put("status", "active");
        return m;
    }

    @Transactional
    public void rejectStaff(Long callerUserId, Long staffUserId) {
        AppUser staff = pendingStaffInCallerSchool(callerUserId, staffUserId);
        Long tenantId = staff.getTenantId();
        String email = staff.getEmail();
        userRepo.delete(staff);   // never activated; removing it lets the person re-apply with the same email
        audit.record(tenantId, callerUserId, "STAFF_REJECTED", email);
    }

    // ---- Admin: manage active staff (suspend / re-activate / remove) ----

    private static final Set<String> STAFF_ROLES = Set.of("ADMIN", "PRINCIPAL", "BURSAR", "TEACHER");

    /** Every staff login of the caller's school (pending ones live in the pending list instead). */
    public List<Map<String, Object>> listStaff(Long callerUserId) {
        Tenant t = callerTenant(callerUserId);
        Map<Long, String> roleNames = roleRepo.findAll().stream()
                .collect(java.util.stream.Collectors.toMap(Role::getId, Role::getName));
        List<Map<String, Object>> out = new ArrayList<>();
        for (AppUser u : userRepo.findByTenantIdOrderByCreatedAtDesc(t.getId())) {
            String role = roleNames.getOrDefault(u.getRoleId(), "?");
            if (!STAFF_ROLES.contains(role) || "pending".equals(u.getAccountStatus())) continue;
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", u.getId());
            row.put("name", u.getFirstName() + " " + u.getLastName());
            row.put("email", u.getEmail());
            row.put("username", u.getUsername());
            row.put("role", role);
            row.put("status", u.getAccountStatus());
            row.put("avatar", u.getAvatar());
            row.put("staffTitle", u.getStaffTitle());
            row.put("self", u.getId().equals(callerUserId));
            out.add(row);
        }
        return out;
    }

    /** Suspend / re-activate a staff login (account_status); a suspended member can't sign in. */
    @Transactional
    public void setStaffStatus(Long callerUserId, Long staffUserId, String status) {
        AppUser staff = staffInCallerSchool(callerUserId, staffUserId);
        staff.setAccountStatus(status);
        userRepo.save(staff);
        audit.record(staff.getTenantId(), callerUserId,
                "suspended".equals(status) ? "STAFF_SUSPENDED" : "STAFF_ACTIVATED", staff.getEmail());
    }

    /** Set/clear a staff member's title (blank clears). Self allowed — an admin may title themself. */
    @Transactional
    public void setStaffTitle(Long callerUserId, Long staffUserId, String title) {
        Tenant t = callerTenant(callerUserId);
        AppUser staff = userRepo.findById(staffUserId)
                .orElseThrow(() -> new EntityNotFoundException("No such staff member"));
        if (!t.getId().equals(staff.getTenantId())) {
            throw new AccessDeniedException("That staff member is not in your school");
        }
        String role = roleRepo.findById(staff.getRoleId()).map(Role::getName).orElse("?");
        if (!STAFF_ROLES.contains(role) && !"LIBRARIAN".equals(role)) {
            throw new IllegalArgumentException("That account is not a staff member");
        }
        String clean = title == null ? null : title.trim();
        if (clean != null && clean.length() > 60) {
            throw new IllegalArgumentException("Titles are 60 characters max");
        }
        staff.setStaffTitle(clean == null || clean.isEmpty() ? null : clean);
        userRepo.save(staff);
        audit.record(staff.getTenantId(), callerUserId, "STAFF_TITLE_SET",
                staff.getEmail() + " -> " + (staff.getStaffTitle() == null ? "(cleared)" : staff.getStaffTitle()));
    }

    /** Remove a staff login. Cascades role_assignment/notification via FK (moderator-delete pattern). */
    @Transactional
    public void deleteStaff(Long callerUserId, Long staffUserId) {
        AppUser staff = staffInCallerSchool(callerUserId, staffUserId);
        Long tenantId = staff.getTenantId();
        String email = staff.getEmail();
        userRepo.delete(staff);
        audit.record(tenantId, callerUserId, "STAFF_REMOVED", email);
    }

    /** Loads a staff member, asserts same school + staff role + not the caller themself. */
    private AppUser staffInCallerSchool(Long callerUserId, Long staffUserId) {
        Tenant t = callerTenant(callerUserId);
        AppUser staff = userRepo.findById(staffUserId)
                .orElseThrow(() -> new EntityNotFoundException("No such staff member"));
        if (!t.getId().equals(staff.getTenantId())) {
            throw new AccessDeniedException("That staff member is not in your school");
        }
        String role = roleRepo.findById(staff.getRoleId()).map(Role::getName).orElse("?");
        if (!STAFF_ROLES.contains(role)) {
            throw new IllegalArgumentException("That account is not a staff member");
        }
        if (staff.getId().equals(callerUserId)) {
            throw new IllegalArgumentException("You cannot suspend or remove your own account");
        }
        return staff;
    }

    // ---- Helpers ----

    /** The school of the calling admin/principal; rejects platform users (no tenant). */
    private Tenant callerTenant(Long callerUserId) {
        AppUser caller = userRepo.findById(callerUserId)
                .orElseThrow(() -> new EntityNotFoundException("User not found"));
        if (caller.getTenantId() == null) {
            throw new AccessDeniedException("Only a school administrator can manage staff onboarding");
        }
        return tenantRepo.findById(caller.getTenantId())
                .orElseThrow(() -> new IllegalStateException("Caller references missing school"));
    }

    /** Loads a pending staff member and asserts they belong to the caller's school. */
    private AppUser pendingStaffInCallerSchool(Long callerUserId, Long staffUserId) {
        Tenant t = callerTenant(callerUserId);
        AppUser staff = userRepo.findById(staffUserId)
                .orElseThrow(() -> new EntityNotFoundException("No such staff request"));
        if (!t.getId().equals(staff.getTenantId())) {
            throw new AccessDeniedException("That staff request is not in your school");
        }
        if (!"pending".equals(staff.getAccountStatus())) {
            throw new TenantService.ConflictException("That account is not awaiting approval");
        }
        return staff;
    }

    private String uniqueUsername(String first, String last) {
        String base = (first + "." + last).toLowerCase().replaceAll("[^a-z0-9.]", "");
        if (base.isBlank() || base.equals(".")) base = "staff";
        String candidate = base;
        for (int n = 2; userRepo.existsByUsernameIgnoreCase(candidate); n++) candidate = base + n;
        return candidate;
    }

    private static String randomCode() {
        StringBuilder sb = new StringBuilder(8);
        for (int i = 0; i < 8; i++) sb.append(CODE_ALPHABET[RANDOM.nextInt(CODE_ALPHABET.length)]);
        return sb.toString();
    }
}
