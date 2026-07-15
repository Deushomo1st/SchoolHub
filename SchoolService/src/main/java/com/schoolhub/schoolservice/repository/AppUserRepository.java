package com.schoolhub.schoolservice.repository;

import com.schoolhub.schoolservice.model.AppUser;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {
    boolean existsByEmailIgnoreCase(String email);
    boolean existsByUsernameIgnoreCase(String username);

    // The public handle lookup (guardian searching for a ward, etc.) - username doubles as the
    // permanent public handle for every account, not just staff logins. See AccountProvisioning.
    Optional<AppUser> findByUsernameIgnoreCase(String username);

    // Used to broadcast a workflow proposal to every Admin when there's no single specific
    // confirmer to target (e.g. an Offering has no owner field the way an OrgUnit does).
    List<AppUser> findByRoleIdAndTenantId(Long roleId, Long tenantId);
}
