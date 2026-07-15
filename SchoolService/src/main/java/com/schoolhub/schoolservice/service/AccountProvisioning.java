package com.schoolhub.schoolservice.service;

import com.schoolhub.schoolservice.exception.ConflictException;
import com.schoolhub.schoolservice.model.AppUser;
import com.schoolhub.schoolservice.model.Role;
import com.schoolhub.schoolservice.model.RoleAssignment;
import com.schoolhub.schoolservice.repository.AppUserRepository;
import com.schoolhub.schoolservice.repository.RoleAssignmentRepository;
import com.schoolhub.schoolservice.repository.RoleRepository;
import com.schoolhub.schoolservice.tenant.TenantContext;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * Creates login accounts (platform.app_user) for the people an ADMIN onboards.
 * The new user is stamped with the ADMIN's own tenant_id (from the request context),
 * so a teacher/student/guardian login is scoped to the same school. Also writes a
 * default role_assignment row alongside the AppUser insert, so Role-as-relationship
 * is backed by real data from the moment an account exists, not just the legacy fields.
 */
@Service
public class AccountProvisioning {

    private final AppUserRepository userRepo;
    private final RoleRepository roleRepo;
    private final RoleAssignmentRepository roleAssignmentRepo;
    private final BCryptPasswordEncoder encoder;
    private final AuditRecorder audit;

    public AccountProvisioning(AppUserRepository userRepo, RoleRepository roleRepo,
                               RoleAssignmentRepository roleAssignmentRepo, BCryptPasswordEncoder encoder,
                               AuditRecorder audit) {
        this.userRepo = userRepo;
        this.roleRepo = roleRepo;
        this.roleAssignmentRepo = roleAssignmentRepo;
        this.encoder = encoder;
        this.audit = audit;
    }

    /** @return the new login's user id. */
    public Long createLogin(String email, String tempPassword, String firstName, String lastName,
                            String phone, String roleName) {
        if (email == null || email.isBlank()) throw new IllegalArgumentException("A login email is required");
        if (tempPassword == null || tempPassword.length() < 8) {
            throw new IllegalArgumentException("Temp password must be at least 8 characters");
        }
        if (userRepo.existsByEmailIgnoreCase(email)) {
            throw new ConflictException("Email '" + email + "' is already registered");
        }
        Role role = roleRepo.findByName(roleName)
                .orElseThrow(() -> new IllegalStateException("Role missing: " + roleName));

        AppUser u = new AppUser();
        u.setEmail(email);
        u.setPasswordHash(encoder.encode(tempPassword));
        u.setFirstName(firstName);
        u.setLastName(lastName);
        u.setPhone(phone);
        u.setRoleId(role.getId());
        u.setTenantId(TenantContext.getTenantId());   // same school as the admin creating them
        u.setAccountStatus("active");
        // Every account gets one - username doubles as the permanent public handle (guardian
        // link-search, etc.), not just a staff sign-in alias.
        u.setUsername(uniqueUsername(firstName, lastName));
        Long id = userRepo.save(u).getId();

        RoleAssignment ra = new RoleAssignment();
        ra.setAppUserId(id);
        ra.setTenantId(TenantContext.getTenantId());
        ra.setRoleId(role.getId());
        ra.setDefault(true);
        ra.setGrantedBy(TenantContext.getUserId());
        roleAssignmentRepo.save(ra);

        audit.record("USER_CREATED", roleName + " account: " + email);
        return id;
    }

    /** first.last (lowercased, alnum only), numbered if the handle is already taken. */
    private String uniqueUsername(String first, String last) {
        String base = (first + "." + last).toLowerCase().replaceAll("[^a-z0-9.]", "");
        if (base.isBlank() || base.equals(".")) base = "user";
        String candidate = base;
        for (int n = 2; userRepo.existsByUsernameIgnoreCase(candidate); n++) candidate = base + n;
        return candidate;
    }
}
