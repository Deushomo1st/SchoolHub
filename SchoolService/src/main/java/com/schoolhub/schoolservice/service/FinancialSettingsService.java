package com.schoolhub.schoolservice.service;

import com.schoolhub.schoolservice.dto.Requests.FinancialSettingsReq;
import com.schoolhub.schoolservice.model.FinancialSettings;
import com.schoolhub.schoolservice.repository.FinancialSettingsRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/** Institution-level currency. One row per tenant schema (id is always 1, seeded by SQL). */
@Service
public class FinancialSettingsService {

    private final FinancialSettingsRepository repo;

    public FinancialSettingsService(FinancialSettingsRepository repo) {
        this.repo = repo;
    }

    public FinancialSettings get() {
        return repo.findById(1L).orElseGet(() -> repo.save(new FinancialSettings()));
    }

    @Transactional
    public FinancialSettings update(FinancialSettingsReq req) {
        FinancialSettings s = get();
        s.setCurrencyCode(req.currencyCode());
        s.setCurrencySymbol(req.currencySymbol());
        s.setUpdatedAt(LocalDateTime.now());
        return repo.save(s);
    }
}
