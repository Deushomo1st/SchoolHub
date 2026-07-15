package com.schoolhub.schoolservice.service;

import com.schoolhub.schoolservice.dto.Requests.CredentialReq;
import com.schoolhub.schoolservice.model.Credential;
import com.schoolhub.schoolservice.repository.CredentialRepository;
import com.schoolhub.schoolservice.tenant.TenantContext;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Proof of completion an institution issues - manually by an Admin, or automatically by
 * ProgressionRuleService when a rule with autoIssueCredential set passes.
 */
@Service
public class CredentialService {

    private final CredentialRepository repo;

    public CredentialService(CredentialRepository repo) {
        this.repo = repo;
    }

    public List<Credential> listForPerson(Long personUserId) {
        return repo.findByPersonUserIdOrderByIssuedAtDesc(personUserId);
    }

    public Credential get(Long id) {
        return repo.findById(id).orElseThrow(() -> new EntityNotFoundException("Credential not found: " + id));
    }

    @Transactional
    public Credential issue(CredentialReq req) {
        Credential c = new Credential();
        c.setPersonUserId(req.personUserId());
        c.setTitle(req.title());
        c.setCriteriaRef(req.criteriaRef() == null ? "manual" : req.criteriaRef());
        c.setArtifactUrl(req.artifactUrl());
        c.setIssuedBy(TenantContext.getUserId());
        return repo.save(c);
    }

    /** Called directly by ProgressionRuleService on a pass - no HTTP round trip needed. */
    @Transactional
    public Credential autoIssue(Long personUserId, String title, String criteriaRef) {
        Credential c = new Credential();
        c.setPersonUserId(personUserId);
        c.setTitle(title);
        c.setCriteriaRef(criteriaRef);
        c.setIssuedBy(TenantContext.getUserId());
        return repo.save(c);
    }

    @Transactional
    public void revoke(Long id) {
        Credential c = get(id);
        c.setStatus("revoked");
        c.setRevokedBy(TenantContext.getUserId());
        c.setRevokedAt(java.time.LocalDateTime.now());
        repo.save(c);
    }

    /** Curated, minimal view for external verification - never the full record. */
    public Map<String, Object> verify(Long id) {
        Credential c = get(id);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("title", c.getTitle());
        m.put("status", c.getStatus());
        m.put("issuedAt", c.getIssuedAt());
        return m;
    }
}
