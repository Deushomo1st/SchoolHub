package com.schoolhub.tenantservice.service;

import com.schoolhub.tenantservice.dto.*;
import com.schoolhub.tenantservice.model.*;
import com.schoolhub.tenantservice.repository.*;
import com.schoolhub.tenantservice.util.SchemaNames;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

/**
 * Owns school signup. The signup is one transaction: register the school in the
 * platform schema, create its first ADMIN, then CREATE the school's own Postgres
 * schema and build its tables from tenant_template.sql. If schema provisioning
 * fails, the whole signup rolls back (Postgres DDL is transactional).
 */
@Service
public class TenantService {

    private final TenantRepository tenantRepo;
    private final SubscriptionPlanRepository planRepo;
    private final RoleRepository roleRepo;
    private final AppUserRepository userRepo;
    private final BCryptPasswordEncoder encoder;
    private final DataSource dataSource;
    private final PlatformNotificationService notif;
    private final BillingService billing;

    public TenantService(TenantRepository tenantRepo, SubscriptionPlanRepository planRepo,
                         RoleRepository roleRepo, AppUserRepository userRepo,
                         BCryptPasswordEncoder encoder, DataSource dataSource,
                         PlatformNotificationService notif, BillingService billing) {
        this.tenantRepo = tenantRepo;
        this.planRepo = planRepo;
        this.roleRepo = roleRepo;
        this.userRepo = userRepo;
        this.encoder = encoder;
        this.dataSource = dataSource;
        this.notif = notif;
        this.billing = billing;
    }

    public List<TemplateDto> templates() {
        return TemplateCatalog.TEMPLATES;
    }

    public List<SubscriptionPlan> plans() {
        return planRepo.findAll();
    }

    @Transactional
    public SignupResponse signup(SignupRequest req) {
        String schema = SchemaNames.normalize(req.getCode());

        // School type is no longer chosen at signup - schools shape their own structure
        // with the in-app designer, so a blank "generic" slate is the default. A caller may
        // still pass a known template key (kept valid for backward compatibility).
        String templateKey = (req.getTemplateKey() == null || req.getTemplateKey().isBlank())
                ? "generic" : req.getTemplateKey();
        if (!TemplateCatalog.isValidKey(templateKey)) {
            throw new IllegalArgumentException("Unknown template: " + req.getTemplateKey());
        }
        if (tenantRepo.existsByCode(schema) || tenantRepo.existsBySchemaName(schema)) {
            throw new ConflictException("School code '" + schema + "' is already taken");
        }
        if (userRepo.existsByEmailIgnoreCase(req.getAdminEmail())) {
            throw new ConflictException("That admin email is already registered");
        }
        SubscriptionPlan plan = planRepo.findByNameIgnoreCase(req.getPlanName())
                .orElseThrow(() -> new IllegalArgumentException("Unknown plan: " + req.getPlanName()));
        Role adminRole = roleRepo.findByName("ADMIN")
                .orElseThrow(() -> new IllegalStateException("ADMIN role missing - DDL was not applied"));

        Tenant tenant = new Tenant();
        tenant.setName(req.getSchoolName());
        tenant.setCode(schema);
        tenant.setSchemaName(schema);
        tenant.setTemplateKey(templateKey);
        tenant.setPlanId(plan.getId());
        tenant.setContactEmail(req.getAdminEmail());
        tenant.setStatus("pending");   // a SchoolHub owner/moderator must approve before the school can sign in
        tenant = tenantRepo.save(tenant);

        notif.broadcastToPlatformTeam("SCHOOL_PENDING", "New school: " + tenant.getName(),
            req.getSchoolName() + " registered with " + req.getPlanName() + " plan. Review and approve.",
            "tenant", tenant.getId());

        AppUser admin = new AppUser();
        admin.setEmail(req.getAdminEmail());
        admin.setPasswordHash(encoder.encode(req.getAdminPassword()));
        admin.setFirstName(req.getAdminFirstName());
        admin.setLastName(req.getAdminLastName());
        admin.setPhone(req.getAdminPhone());
        admin.setRoleId(adminRole.getId());
        admin.setTenantId(tenant.getId());
        admin.setAccountStatus("active");
        userRepo.save(admin);

        provisionSchema(schema);

        // Kick off Stripe Checkout for paid plans (null for free / Stripe-off / errors).
        String checkoutUrl = billing.checkoutForNewTenant(tenant, plan);

        return new SignupResponse(tenant.getId(), tenant.getName(), schema, schema, req.getAdminEmail(),
                checkoutUrl != null
                        ? "School registered! Complete your subscription to activate."
                        : "School registered. A SchoolHub administrator will review and approve it shortly; "
                                + "the admin can sign in once it is approved.",
                checkoutUrl);
    }

    /**
     * Creates the school's schema and builds its tables. Runs on the transaction's
     * connection; SET LOCAL search_path auto-resets at commit so the pooled
     * connection never leaks a tenant search_path to the next request.
     */
    private void provisionSchema(String schema) {
        Connection conn = DataSourceUtils.getConnection(dataSource);
        try (Statement st = conn.createStatement()) {
            st.execute("CREATE SCHEMA \"" + schema + "\"");          // schema is allow-list validated
            st.execute("SET LOCAL search_path TO \"" + schema + "\"");
            ScriptUtils.executeSqlScript(conn, new ClassPathResource("tenant_template.sql"));
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to provision schema '" + schema + "': " + e.getMessage(), e);
        } finally {
            DataSourceUtils.releaseConnection(conn, dataSource);     // no-op inside the active transaction
        }
    }

    public static class ConflictException extends RuntimeException {
        public ConflictException(String msg) { super(msg); }
    }
}
