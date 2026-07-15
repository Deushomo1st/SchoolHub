package com.schoolhub.schoolservice.service;

import com.schoolhub.schoolservice.dto.Requests.OfferingReq;
import com.schoolhub.schoolservice.exception.ConflictException;
import com.schoolhub.schoolservice.model.Offering;
import com.schoolhub.schoolservice.model.OfferingOrgUnit;
import com.schoolhub.schoolservice.model.OfferingPrerequisite;
import com.schoolhub.schoolservice.repository.OfferingOrgUnitRepository;
import com.schoolhub.schoolservice.repository.OfferingPrerequisiteRepository;
import com.schoolhub.schoolservice.repository.OfferingRepository;
import com.schoolhub.schoolservice.repository.OrgUnitRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Self-referential Offering tree (Program -> Course -> Module, same recursion trick as OrgUnit),
 * cross-listable across Org Units, with configurable prerequisites.
 */
@Service
public class OfferingService {

    private final OfferingRepository repo;
    private final OfferingOrgUnitRepository crossListRepo;
    private final OfferingPrerequisiteRepository prereqRepo;
    private final OrgUnitRepository orgUnitRepo;

    public OfferingService(OfferingRepository repo, OfferingOrgUnitRepository crossListRepo,
                           OfferingPrerequisiteRepository prereqRepo, OrgUnitRepository orgUnitRepo) {
        this.repo = repo;
        this.crossListRepo = crossListRepo;
        this.prereqRepo = prereqRepo;
        this.orgUnitRepo = orgUnitRepo;
    }

    public List<Offering> list() { return repo.findAllByOrderByTitleAsc(); }

    public List<Offering> roots() { return repo.findByParentIdIsNullOrderByTitleAsc(); }

    public Offering get(Long id) {
        return repo.findById(id).orElseThrow(() -> new EntityNotFoundException("Offering not found: " + id));
    }

    public List<Offering> children(Long id) {
        if (!repo.existsById(id)) throw new EntityNotFoundException("Offering not found: " + id);
        return repo.findByParentIdOrderByTitleAsc(id);
    }

    public List<Offering> ancestors(Long id) {
        Offering current = get(id);
        List<Offering> chain = new ArrayList<>();
        Long parentId = current.getParentId();
        while (parentId != null) {
            Offering parent = get(parentId);
            chain.add(parent);
            parentId = parent.getParentId();
        }
        Collections.reverse(chain);
        return chain;
    }

    @Transactional
    public Offering create(OfferingReq req) {
        if (req.parentId() != null && !repo.existsById(req.parentId())) {
            throw new EntityNotFoundException("Parent offering not found: " + req.parentId());
        }
        Offering o = new Offering();
        o.setParentId(req.parentId());
        o.setTitle(req.title());
        o.setCreditWeight(req.creditWeight());
        return repo.save(o);
    }

    // ---- Cross-listing (one Offering, many Org Units) ----

    public List<OfferingOrgUnit> crossListings(Long offeringId) {
        return crossListRepo.findByOfferingId(offeringId);
    }

    @Transactional
    public OfferingOrgUnit addCrossListing(Long offeringId, Long orgUnitId) {
        if (!repo.existsById(offeringId)) throw new EntityNotFoundException("Offering not found: " + offeringId);
        if (!orgUnitRepo.existsById(orgUnitId)) throw new EntityNotFoundException("Org unit not found: " + orgUnitId);
        if (crossListRepo.existsByOfferingIdAndOrgUnitId(offeringId, orgUnitId)) {
            throw new ConflictException("That offering is already cross-listed under that org unit");
        }
        OfferingOrgUnit link = new OfferingOrgUnit();
        link.setOfferingId(offeringId);
        link.setOrgUnitId(orgUnitId);
        return crossListRepo.save(link);
    }

    @Transactional
    public void removeCrossListing(Long offeringId, Long orgUnitId) {
        OfferingOrgUnit link = crossListRepo.findByOfferingIdAndOrgUnitId(offeringId, orgUnitId)
                .orElseThrow(() -> new EntityNotFoundException("That cross-listing does not exist"));
        crossListRepo.delete(link);
    }

    // ---- Prerequisites ----

    public List<OfferingPrerequisite> prerequisites(Long offeringId) {
        return prereqRepo.findByOfferingId(offeringId);
    }

    @Transactional
    public OfferingPrerequisite addPrerequisite(Long offeringId, Long prerequisiteId) {
        if (offeringId.equals(prerequisiteId)) {
            throw new IllegalArgumentException("An offering cannot be its own prerequisite");
        }
        if (!repo.existsById(offeringId)) throw new EntityNotFoundException("Offering not found: " + offeringId);
        if (!repo.existsById(prerequisiteId)) {
            throw new EntityNotFoundException("Prerequisite offering not found: " + prerequisiteId);
        }
        if (prereqRepo.existsByOfferingIdAndPrerequisiteId(offeringId, prerequisiteId)) {
            throw new ConflictException("That prerequisite is already set");
        }
        OfferingPrerequisite p = new OfferingPrerequisite();
        p.setOfferingId(offeringId);
        p.setPrerequisiteId(prerequisiteId);
        return prereqRepo.save(p);
    }

    @Transactional
    public void removePrerequisite(Long offeringId, Long prerequisiteId) {
        OfferingPrerequisite p = prereqRepo.findByOfferingIdAndPrerequisiteId(offeringId, prerequisiteId)
                .orElseThrow(() -> new EntityNotFoundException("That prerequisite does not exist"));
        prereqRepo.delete(p);
    }
}
