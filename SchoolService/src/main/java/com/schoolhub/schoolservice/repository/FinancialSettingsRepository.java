package com.schoolhub.schoolservice.repository;

import com.schoolhub.schoolservice.model.FinancialSettings;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FinancialSettingsRepository extends JpaRepository<FinancialSettings, Long> {
}
