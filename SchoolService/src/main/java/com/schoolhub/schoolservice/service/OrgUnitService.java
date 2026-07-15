package com.schoolhub.schoolservice.service;

import com.schoolhub.schoolservice.dto.Requests.OrgUnitReq;
import com.schoolhub.schoolservice.model.OrgUnit;
import com.schoolhub.schoolservice.repository.OrgUnitRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;

/**
 * Self-referential Org Unit tree, unlimited depth: Institution (root) -> Campus -> Faculty ->
 * Department... Deletion itself is orchestrated by WorkflowService (the 7-day protest window,
 * "everyone in the branch" notify step) - this class only exposes the small state-mutating
 * primitives that workflow needs (flip to pending_deletion, revert, hard-delete, list descendants).
 */
@Service
public class OrgUnitService {

    private final OrgUnitRepository repo;

    public OrgUnitService(OrgUnitRepository repo) {
        this.repo = repo;
    }

    public List<OrgUnit> list() { return repo.findAllByOrderByNameAsc(); }

    public List<OrgUnit> roots() { return repo.findByParentIdIsNullOrderByNameAsc(); }

    public OrgUnit get(Long id) {
        return repo.findById(id).orElseThrow(() -> new EntityNotFoundException("Org unit not found: " + id));
    }

    public List<OrgUnit> children(Long id) {
        if (!repo.existsById(id)) throw new EntityNotFoundException("Org unit not found: " + id);
        return repo.findByParentIdOrderByNameAsc(id);
    }

    /** Path from the root down to (but not including) this unit. */
    public List<OrgUnit> ancestors(Long id) {
        OrgUnit current = get(id);
        List<OrgUnit> chain = new ArrayList<>();
        Long parentId = current.getParentId();
        while (parentId != null) {
            OrgUnit parent = get(parentId);
            chain.add(parent);
            parentId = parent.getParentId();
        }
        Collections.reverse(chain);
        return chain;
    }

    @Transactional
    public OrgUnit create(OrgUnitReq req) {
        if (req.parentId() != null && !repo.existsById(req.parentId())) {
            throw new EntityNotFoundException("Parent org unit not found: " + req.parentId());
        }
        OrgUnit u = new OrgUnit();
        u.setParentId(req.parentId());
        u.setName(req.name());
        u.setTypeLabel(req.typeLabel());
        u.setOwnerUserId(req.ownerUserId());
        return repo.save(u);
    }

    /** This unit's id plus every descendant's id (BFS) - who's "in the branch" for a deletion notify. */
    public List<Long> descendantIds(Long rootId) {
        List<Long> ids = new ArrayList<>();
        Deque<Long> queue = new ArrayDeque<>();
        queue.add(rootId);
        while (!queue.isEmpty()) {
            Long current = queue.poll();
            ids.add(current);
            for (OrgUnit child : repo.findByParentIdOrderByNameAsc(current)) queue.add(child.getId());
        }
        return ids;
    }

    @Transactional
    public void setStatus(Long id, String status) {
        OrgUnit u = get(id);
        u.setStatus(status);
        repo.save(u);
    }

    @Transactional
    public void hardDelete(Long id) {
        if (!repo.existsById(id)) throw new EntityNotFoundException("Org unit not found: " + id);
        repo.deleteById(id);
    }
}
