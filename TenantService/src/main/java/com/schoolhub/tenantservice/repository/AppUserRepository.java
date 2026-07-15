package com.schoolhub.tenantservice.repository;

import com.schoolhub.tenantservice.model.AppUser;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {
    boolean existsByEmailIgnoreCase(String email);
    boolean existsByUsernameIgnoreCase(String username);
    long countByTenantId(Long tenantId);
    List<AppUser> findByTenantIdAndAccountStatusOrderByCreatedAtDesc(Long tenantId, String accountStatus);
}
