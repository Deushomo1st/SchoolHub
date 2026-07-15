package com.schoolhub.schoolservice.service;

import com.schoolhub.schoolservice.dto.Requests.SchedulePeriodReq;
import com.schoolhub.schoolservice.model.SchedulePeriod;
import com.schoolhub.schoolservice.repository.SchedulePeriodRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Self-referential Schedule Period tree (Year -> Term -> Week). Phase 1 only had a hand-seeded
 * stub row with no API; this is the real CRUD/tree service for it.
 */
@Service
public class SchedulePeriodService {

    private final SchedulePeriodRepository repo;

    public SchedulePeriodService(SchedulePeriodRepository repo) {
        this.repo = repo;
    }

    public List<SchedulePeriod> list() { return repo.findAllByOrderByStartDateDesc(); }

    public List<SchedulePeriod> roots() { return repo.findByParentIdIsNullOrderByStartDateDesc(); }

    public SchedulePeriod get(Long id) {
        return repo.findById(id).orElseThrow(() -> new EntityNotFoundException("Schedule period not found: " + id));
    }

    public List<SchedulePeriod> children(Long id) {
        if (!repo.existsById(id)) throw new EntityNotFoundException("Schedule period not found: " + id);
        return repo.findByParentId(id);
    }

    public List<SchedulePeriod> ancestors(Long id) {
        SchedulePeriod current = get(id);
        List<SchedulePeriod> chain = new ArrayList<>();
        Long parentId = current.getParentId();
        while (parentId != null) {
            SchedulePeriod parent = get(parentId);
            chain.add(parent);
            parentId = parent.getParentId();
        }
        Collections.reverse(chain);
        return chain;
    }

    @Transactional
    public SchedulePeriod create(SchedulePeriodReq req) {
        if (req.parentId() != null && !repo.existsById(req.parentId())) {
            throw new EntityNotFoundException("Parent schedule period not found: " + req.parentId());
        }
        SchedulePeriod p = new SchedulePeriod();
        p.setParentId(req.parentId());
        p.setLabel(req.label());
        p.setStartDate(req.startDate());
        p.setEndDate(req.endDate());
        return repo.save(p);
    }
}
