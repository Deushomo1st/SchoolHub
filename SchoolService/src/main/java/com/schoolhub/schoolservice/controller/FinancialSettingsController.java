package com.schoolhub.schoolservice.controller;

import com.schoolhub.schoolservice.dto.Requests.FinancialSettingsReq;
import com.schoolhub.schoolservice.service.FinancialSettingsService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/financial-settings")
public class FinancialSettingsController {

    private final FinancialSettingsService service;

    public FinancialSettingsController(FinancialSettingsService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','BURSAR')")
    public ResponseEntity<?> get() { return ResponseEntity.ok(service.get()); }

    @PutMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> update(@Valid @RequestBody FinancialSettingsReq req) {
        return ResponseEntity.ok(service.update(req));
    }
}
