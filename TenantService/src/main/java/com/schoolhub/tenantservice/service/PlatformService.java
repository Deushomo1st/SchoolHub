package com.schoolhub.tenantservice.service;

import com.schoolhub.tenantservice.dto.ModeratorRequest;
import com.schoolhub.tenantservice.model.*;
import com.schoolhub.tenantservice.repository.*;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/** Super-admin / moderator operations over the whole platform (all schools). */
@Service
public class PlatformService {

    private final TenantRepository tenantRepo;
    private final SubscriptionPlanRepository planRepo;
    private final AppUserRepository userRepo;
    private final RoleRepository roleRepo;
    private final BCryptPasswordEncoder encoder;
    private final AuditService audit;

    public PlatformService(TenantRepository tenantRepo, SubscriptionPlanRepository planRepo,
                           AppUserRepository userRepo, RoleRepository roleRepo, BCryptPasswordEncoder encoder,
                           AuditService audit) {
        this.tenantRepo = tenantRepo;
        this.planRepo = planRepo;
        this.userRepo = userRepo;
        this.roleRepo = roleRepo;
        this.encoder = encoder;
        this.audit = audit;
    }

    public List<Map<String, Object>> listSchools() {
        Map<Long, String> planNames = planRepo.findAll().stream()
                .collect(Collectors.toMap(SubscriptionPlan::getId, SubscriptionPlan::getName));
        return tenantRepo.findAll().stream()
                .sorted(Comparator.comparing(Tenant::getId))
                .map(t -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", t.getId());
                    m.put("name", t.getName());
                    m.put("code", t.getCode());
                    m.put("schemaName", t.getSchemaName());
                    m.put("templateKey", t.getTemplateKey());
                    m.put("plan", planNames.get(t.getPlanId()));
                    m.put("status", t.getStatus());
                    m.put("contactEmail", t.getContactEmail());
                    m.put("users", userRepo.countByTenantId(t.getId()));
                    m.put("createdAt", t.getCreatedAt());
                    return m;
                })
                .collect(Collectors.toList());
    }

    public Map<String, Object> stats() {
        List<Tenant> all = tenantRepo.findAll();
        Map<Long, String> planNames = planRepo.findAll().stream()
                .collect(Collectors.toMap(SubscriptionPlan::getId, SubscriptionPlan::getName));
        Map<String, Long> byPlan = all.stream()
                .collect(Collectors.groupingBy(t -> planNames.getOrDefault(t.getPlanId(), "?"), Collectors.counting()));
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("totalSchools", all.size());
        m.put("active", all.stream().filter(t -> "active".equals(t.getStatus())).count());
        m.put("pending", all.stream().filter(t -> "pending".equals(t.getStatus())).count());
        m.put("suspended", all.stream().filter(t -> "suspended".equals(t.getStatus())).count());
        m.put("totalUsers", userRepo.count());
        m.put("byPlan", byPlan);
        return m;
    }

    /** Every login on SchoolHub, grouped into friendly categories with a flat list. */
    public Map<String, Object> listUsers() {
        Map<Long, String> roleNames = roleRepo.findAll().stream()
                .collect(Collectors.toMap(Role::getId, Role::getName));
        Map<Long, String> schoolNames = tenantRepo.findAll().stream()
                .collect(Collectors.toMap(Tenant::getId, Tenant::getName));

        // category -> count, in display order
        Map<String, Long> categories = new LinkedHashMap<>();
        for (String c : new String[]{"Platform team", "School staff", "Students", "Guardians"}) categories.put(c, 0L);

        List<Map<String, Object>> users = new ArrayList<>();
        for (AppUser u : userRepo.findAll()) {
            String role = roleNames.getOrDefault(u.getRoleId(), "?");
            String category = categoryOf(role);
            categories.merge(category, 1L, Long::sum);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", u.getId());
            row.put("name", u.getFirstName() + " " + u.getLastName());
            row.put("email", u.getEmail());
            row.put("role", role);
            row.put("category", category);
            row.put("school", u.getTenantId() == null ? "Platform" : schoolNames.getOrDefault(u.getTenantId(), "?"));
            row.put("status", u.getAccountStatus());
            users.add(row);
        }
        users.sort(Comparator.comparing(r -> (String) r.get("category")));

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("categories", categories);
        out.put("users", users);
        return out;
    }

    private static String categoryOf(String role) {
        return switch (role) {
            case "PLATFORM_OWNER", "MODERATOR" -> "Platform team";
            case "ADMIN", "PRINCIPAL", "BURSAR", "TEACHER" -> "School staff";
            case "STUDENT" -> "Students";
            case "PARENT" -> "Guardians";
            default -> "Other";
        };
    }

    // ---- Subscription plans (super-admin pricing deck) ----
    @Transactional
    public SubscriptionPlan createPlan(com.schoolhub.tenantservice.dto.PlanRequest req, Long actorId) {
        if (planRepo.findByNameIgnoreCase(req.name()).isPresent()) {
            throw new TenantService.ConflictException("A plan named '" + req.name() + "' already exists");
        }
        SubscriptionPlan p = new SubscriptionPlan();
        p.setName(req.name());
        p.setPriceNaira(req.priceNaira());
        p.setMaxStudents(req.maxStudents());
        p.setDescription(req.description() == null ? "" : req.description());
        p = planRepo.save(p);
        audit.record(null, actorId, "PLAN_CREATED", req.name() + " - ₦" + req.priceNaira());
        return p;
    }

    @Transactional
    public SubscriptionPlan updatePlan(Long id, com.schoolhub.tenantservice.dto.PlanRequest req, Long actorId) {
        SubscriptionPlan p = planRepo.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Plan not found: " + id));
        p.setName(req.name());
        p.setPriceNaira(req.priceNaira());
        p.setMaxStudents(req.maxStudents());
        if (req.description() != null) p.setDescription(req.description());
        audit.record(null, actorId, "PLAN_UPDATED", req.name() + " - ₦" + req.priceNaira());
        return planRepo.save(p);
    }

    @Transactional
    public void deletePlan(Long id, Long actorId) {
        SubscriptionPlan p = planRepo.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Plan not found: " + id));
        String name = p.getName();
        try {
            planRepo.delete(p);
            planRepo.flush();   // surface the FK violation now if a school still uses this plan
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            throw new TenantService.ConflictException("That plan is in use by a school and can't be deleted");
        }
        audit.record(null, actorId, "PLAN_DELETED", name);
    }

    @Transactional
    public Tenant setStatus(Long id, String status, Long actorId) {
        Tenant t = tenantRepo.findById(id).orElseThrow(() -> new EntityNotFoundException("School not found: " + id));
        t.setStatus(status);
        Tenant saved = tenantRepo.save(t);
        audit.record(id, actorId, "SCHOOL_" + status.toUpperCase(), t.getName());
        return saved;
    }

    @Transactional
    public Map<String, Object> createModerator(ModeratorRequest req, Long actorId) {
        if (userRepo.existsByEmailIgnoreCase(req.email())) {
            throw new TenantService.ConflictException("Email '" + req.email() + "' is already registered");
        }
        Role mod = roleRepo.findByName("MODERATOR")
                .orElseThrow(() -> new IllegalStateException("MODERATOR role missing - DDL not applied"));
        AppUser u = new AppUser();
        u.setEmail(req.email());
        u.setPasswordHash(encoder.encode(req.password()));
        u.setFirstName(req.firstName());
        u.setLastName(req.lastName());
        u.setPhone(req.phone());
        u.setRoleId(mod.getId());
        u.setTenantId(null);              // platform-level, not tied to a school
        u.setAccountStatus("active");
        u = userRepo.save(u);
        audit.record(null, actorId, "MODERATOR_CREATED", req.email());
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", u.getId());
        m.put("email", req.email());
        m.put("role", "MODERATOR");
        return m;
    }
}
