package com.schoolhub.schoolservice.service;

import com.schoolhub.schoolservice.dto.Requests.CohortReq;
import com.schoolhub.schoolservice.dto.Requests.GroupReq;
import com.schoolhub.schoolservice.exception.ConflictException;
import com.schoolhub.schoolservice.model.Cohort;
import com.schoolhub.schoolservice.model.Group;
import com.schoolhub.schoolservice.repository.CohortRepository;
import com.schoolhub.schoolservice.repository.GroupRepository;
import com.schoolhub.schoolservice.repository.SchedulePeriodRepository;
import com.schoolhub.schoolservice.tenant.TenantContext;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Cohorts (flat - never nested) and the Groups that nest inside them. */
@Service
public class CohortService {

    private final CohortRepository repo;
    private final GroupRepository groupRepo;
    private final SchedulePeriodRepository periodRepo;

    public CohortService(CohortRepository repo, GroupRepository groupRepo, SchedulePeriodRepository periodRepo) {
        this.repo = repo;
        this.groupRepo = groupRepo;
        this.periodRepo = periodRepo;
    }

    public List<Cohort> list() { return repo.findAllByOrderByNameAsc(); }

    public Cohort get(Long id) {
        return repo.findById(id).orElseThrow(() -> new EntityNotFoundException("Cohort not found: " + id));
    }

    @Transactional
    public Cohort create(CohortReq req) {
        if (!periodRepo.existsById(req.schedulePeriodId())) {
            throw new EntityNotFoundException("Schedule period not found: " + req.schedulePeriodId());
        }
        if (repo.existsBySchedulePeriodIdAndName(req.schedulePeriodId(), req.name())) {
            throw new ConflictException("Cohort '" + req.name() + "' already exists for that period");
        }
        Cohort c = new Cohort();
        c.setSchedulePeriodId(req.schedulePeriodId());
        c.setName(req.name());
        c.setLevelLabel(req.levelLabel());
        c.setHeadUserId(req.headUserId());
        c.setCapacity(req.capacity());
        if (req.primeLevel() != null) c.setPrimeLevel(req.primeLevel());
        return repo.save(c);
    }

    @Transactional
    public void delete(Long id) {
        if (!repo.existsById(id)) throw new EntityNotFoundException("Cohort not found: " + id);
        repo.deleteById(id);
    }

    // ---- Groups (nest inside a Cohort: informal sub-teams, or an Offering-subset) ----

    public List<Group> listGroups(Long cohortId) { return groupRepo.findByCohortId(cohortId); }

    @Transactional
    public Group createGroup(GroupReq req) {
        if (!repo.existsById(req.cohortId())) {
            throw new EntityNotFoundException("Cohort not found: " + req.cohortId());
        }
        Group g = new Group();
        g.setCohortId(req.cohortId());
        g.setOfferingId(req.offeringId());
        g.setName(req.name());
        g.setCreatedBy(TenantContext.getUserId());
        return groupRepo.save(g);
    }
}
