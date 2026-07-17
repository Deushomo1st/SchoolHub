package com.schoolhub.tenantservice.repository;

import com.schoolhub.tenantservice.model.Tenant;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TenantRepository extends JpaRepository<Tenant, Long> {
    boolean existsByCode(String code);
    boolean existsBySchemaName(String schemaName);
    boolean existsByStaffCode(String staffCode);
    Optional<Tenant> findByStaffCode(String staffCode);
    Optional<Tenant> findByStripeCustomerId(String stripeCustomerId);
}
