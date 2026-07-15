package com.schoolhub.schoolservice.service;

import com.schoolhub.schoolservice.model.AuditLog;
import com.schoolhub.schoolservice.repository.AuditLogRepository;
import com.schoolhub.schoolservice.tenant.TenantContext;
import org.springframework.stereotype.Component;

/** Records audit entries stamped with the current request's school + actor. */
@Component
public class AuditRecorder {

    private final AuditLogRepository repo;

    public AuditRecorder(AuditLogRepository repo) {
        this.repo = repo;
    }

    public void record(String action, String detail) {
        AuditLog a = new AuditLog();
        a.setTenantId(TenantContext.getTenantId());
        a.setActorId(TenantContext.getUserId());
        a.setAction(action);
        a.setDetail(detail);
        repo.save(a);
    }
}
