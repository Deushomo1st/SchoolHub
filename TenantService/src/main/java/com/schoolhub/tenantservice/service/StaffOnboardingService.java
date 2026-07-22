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
 * Staff self-onboarding: a school issues per-role codes (teacher, bursar, librarian),
 * staff sign up with the matching code into that school as PENDING, and the school
 * admin approves before they can sign in. On approval the service calls SchoolService
 * to create a typed profile row (teacher, bursar, or library_staff).
 */
@Service
public class StaffOnboardingService {

    private static final char[] CODE_ALPHABET = "ABCDEFGHJKMNPQRSTVWXYZ23456789".toCharArray();
    private static final Set<String> SELF_SIGNUP_ROLES = Set.of("TEACHER", "BURSAR", "LIBRARIAN");
    private static final SecureRandom RANDOM = new SecureRandom();

    private final TenantRepository tenantRepo;
    private final AppUserRepository userRepo;
    private final RoleRepository roleRepo;
    private final BCryptPasswordEncoder encoder;
    private final AuditService audit;
    private final SchoolServiceClient school;

    public StaffOnboardingService(TenantRepository tenantRepo, AppUserRepository userRepo,
                                  RoleRepository roleRepo, BCryptPasswordEncoder encoder,
                                  AuditService audit, SchoolServiceClient school) {
        this.tenantRepo = tenantRepo;
        this.userRepo = userRepo;
        this.roleRepo = roleRepo;
        this.encoder = encoder;
        this.audit = audit;
        this.school = school;
    }

    // ---- Admin: view / generate per-role codes ----

    /** Return all active staff codes for the caller's school. */
    public Map<String, String> allStaffCodes(Long callerUserId) {
        Tenant t = callerTenant(callerUserId);
        Map<String, String> m = new LinkedHashMap<>();
        m.put("teacher", t.getTeacherCode());
        m.put("bursar", t.getBursarCode());
        m.put("librarian", t.getLibrarianCode());
        return m;
    }

    @Transactional
    public String generateTeacherCode(Long callerUserId) {
        Tenant t = callerTenant(callerUserId);
        String code;
        do { code = randomCode(); } while (tenantRepo.findByTeacherCode(code).isPresent());
        t.setTeacherCode(code);
        tenantRepo.save(t);
        audit.record(t.getId(), callerUserId, "TEACHER_CODE_ISSUED", t.getName());
        return code;
    }

    @Transactional
    public String generateBursarCode(Long callerUserId) {
        Tenant t = callerTenant(callerUserId);
        String code;
        do { code = randomCode(); } while (tenantRepo.findByBursarCode(code).isPresent());
        t.setBursarCode(code);
        tenantRepo.save(t);
        audit.record(t.getId(), callerUserId, "BURSAR_CODE_ISSUED", t.getName());
        return code;
    }

    @Transactional
    public String generateLibrarianCode(Long callerUserId) {
        Tenant t = callerTenant(callerUserId);
        String code;
        do { code = randomCode(); } while (tenantRepo.findByLibrarianCode(code).isPresent());
        t.setLibrarianCode(code);
        tenantRepo.save(t);
        audit.record(t.getId(), callerUserId, "LIBRARIAN_CODE_ISSUED", t.getName());
        return code;
    }

    // ---- Public: staff signs up with the per-role code ----

    @Transactional
    public Map<String, Object> staffSignup(StaffSignupRequest req) {
        String code = req.code().trim().toUpperCase();

        // Try to find a tenant by any of the per-role codes
        Tenant tenant = tenantRepo.findByTeacherCode(code)
                .or(() -> tenantRepo.findByBursarCode(code))
                .or(() -> tenantRepo.findByLibrarianCode(code))
                .orElseThrow(() -> new IllegalArgumentException("That staff code is not valid"));

        // Determine role from which code matched
        String roleName;
        if (code.equals(tenant.getTeacherCode())) {
            roleName = "TEACHER";
        } else if (code.equals(tenant.getBursarCode())) {
            roleName = "BURSAR";
        } else if (code.equals(tenant.getLibrarianCode())) {
            roleName = "LIBRARIAN";
        } else {
            throw new IllegalArgumentException("That staff code is not valid");
        }

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
        u.setAccountStatus("pending");
        userRepo.save(u);
        audit.record(tenant.getId(), null, "STAFF_SIGNUP_REQUESTED", roleName + ": " + req.email());

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("school", tenant.getName());
        m.put("role", roleName);
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

    /**
     * Approve a pending staff member, activate their login, and create a typed
     * profile row in SchoolService linked by userId.
     */
    @Transactional
    public Map<String, Object> approveStaff(Long callerUserId, Long staffUserId) {
        AppUser staff = pendingStaffInCallerSchool(callerUserId, staffUserId);
        staff.setAccountStatus("active");
        userRepo.save(staff);
        audit.record(staff.getTenantId(), callerUserId, "STAFF_APPROVED", staff.getEmail());

        String role = roleRepo.findById(staff.getRoleId()).map(Role::getName).orElse("?");
        Tenant tenant = tenantRepo.findById(staff.getTenantId())
                .orElseThrow(() -> new IllegalStateException("Tenant missing for approved staff"));

        // Build profile body and call SchoolService
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("userId", staff.getId().toString());
        body.put("staffNo", staff.getUsername());   // default: username is the staff number
        body.put("firstName", staff.getFirstName());
        body.put("lastName", staff.getLastName());
        body.put("email", staff.getEmail());
        body.put("phone", staff.getPhone());

        try {
            school.createStaffProfile(role, body);
        } catch (Exception e) {
            // Log but don't roll back the approval — the user is active; admin can
            // manually fix the profile later via People section.
            audit.record(staff.getTenantId(), callerUserId, "STAFF_PROFILE_FAILED",
                    role + " " + staff.getEmail() + " — " + e.getMessage());
        }

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
        userRepo.delete(staff);
        audit.record(tenantId, callerUserId, "STAFF_REJECTED", email);
    }

    // ---- Admin: manage active staff (suspend / re-activate / remove) ----

    private static final Set<String> STAFF_ROLES = Set.of("ADMIN", "PRINCIPAL", "BURSAR", "TEACHER", "LIBRARIAN");

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

    @Transactional
    public void setStaffStatus(Long callerUserId, Long staffUserId, String status) {
        AppUser staff = staffInCallerSchool(callerUserId, staffUserId);
        staff.setAccountStatus(status);
        userRepo.save(staff);
        audit.record(staff.getTenantId(), callerUserId,
                "suspended".equals(status) ? "STAFF_SUSPENDED" : "STAFF_ACTIVATED", staff.getEmail());
    }

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

    @Transactional
    public void deleteStaff(Long callerUserId, Long staffUserId) {
        AppUser staff = staffInCallerSchool(callerUserId, staffUserId);
        Long tenantId = staff.getTenantId();
        String email = staff.getEmail();
        userRepo.delete(staff);
        audit.record(tenantId, callerUserId, "STAFF_REMOVED", email);
    }

    // ---- Helpers ----

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

    private Tenant callerTenant(Long callerUserId) {
        AppUser caller = userRepo.findById(callerUserId)
                .orElseThrow(() -> new EntityNotFoundException("User not found"));
        if (caller.getTenantId() == null) {
            throw new AccessDeniedException("Only a school administrator can manage staff onboarding");
        }
        return tenantRepo.findById(caller.getTenantId())
                .orElseThrow(() -> new IllegalStateException("Caller references missing school"));
    }

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
