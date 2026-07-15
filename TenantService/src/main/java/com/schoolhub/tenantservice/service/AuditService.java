package com.schoolhub.tenantservice.service;

import com.schoolhub.tenantservice.model.AppUser;
import com.schoolhub.tenantservice.model.AuditLog;
import com.schoolhub.tenantservice.model.Tenant;
import com.schoolhub.tenantservice.repository.AppUserRepository;
import com.schoolhub.tenantservice.repository.AuditLogRepository;
import com.schoolhub.tenantservice.repository.TenantRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/** Writes and reads the platform audit trail. */
@Service
public class AuditService {

    private final AuditLogRepository auditRepo;
    private final AppUserRepository userRepo;
    private final TenantRepository tenantRepo;

    public AuditService(AuditLogRepository auditRepo, AppUserRepository userRepo, TenantRepository tenantRepo) {
        this.auditRepo = auditRepo;
        this.userRepo = userRepo;
        this.tenantRepo = tenantRepo;
    }

    @Transactional
    public void record(Long tenantId, Long actorId, String action, String detail) {
        AuditLog a = new AuditLog();
        a.setTenantId(tenantId);
        a.setActorId(actorId);
        a.setAction(action);
        a.setDetail(detail);
        auditRepo.save(a);
    }

    public List<Map<String, Object>> list() {
        Map<Long, String> emails = userRepo.findAll().stream()
                .collect(Collectors.toMap(AppUser::getId, AppUser::getEmail));
        Map<Long, String> schools = tenantRepo.findAll().stream()
                .collect(Collectors.toMap(Tenant::getId, Tenant::getName));
        return auditRepo.findTop200ByOrderByCreatedAtDesc().stream().map(a -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("at", a.getCreatedAt());
            m.put("actor", a.getActorId() == null ? "system" : emails.getOrDefault(a.getActorId(), "user #" + a.getActorId()));
            m.put("action", a.getAction());
            m.put("detail", a.getDetail());
            m.put("school", a.getTenantId() == null ? "-" : schools.getOrDefault(a.getTenantId(), "-"));
            return m;
        }).collect(Collectors.toList());
    }
}
