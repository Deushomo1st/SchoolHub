package com.schoolhub.authservice.repository;

import com.schoolhub.authservice.model.Tenant;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TenantRepository extends JpaRepository<Tenant, Long> {
}
