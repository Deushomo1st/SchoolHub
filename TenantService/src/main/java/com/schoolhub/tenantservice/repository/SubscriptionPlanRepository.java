package com.schoolhub.tenantservice.repository;

import com.schoolhub.tenantservice.model.SubscriptionPlan;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SubscriptionPlanRepository extends JpaRepository<SubscriptionPlan, Long> {
    Optional<SubscriptionPlan> findByNameIgnoreCase(String name);
}
