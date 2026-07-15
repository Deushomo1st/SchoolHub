package com.schoolhub.schoolservice.repository;

import com.schoolhub.schoolservice.model.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {
}
