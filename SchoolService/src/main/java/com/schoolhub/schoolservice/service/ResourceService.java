package com.schoolhub.schoolservice.service;

import com.schoolhub.schoolservice.dto.Requests.ResourceReq;
import com.schoolhub.schoolservice.exception.ConflictException;
import com.schoolhub.schoolservice.model.Resource;
import com.schoolhub.schoolservice.model.ResourcePermit;
import com.schoolhub.schoolservice.repository.ResourcePermitRepository;
import com.schoolhub.schoolservice.repository.ResourceRepository;
import com.schoolhub.schoolservice.tenant.TenantContext;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Institution-wide rooms/virtual rooms/equipment. Any lecturer can book one (see SessionService);
 *  a staff_only Resource needs an Admin-granted permit for a student to access. */
@Service
public class ResourceService {

    private final ResourceRepository repo;
    private final ResourcePermitRepository permitRepo;

    public ResourceService(ResourceRepository repo, ResourcePermitRepository permitRepo) {
        this.repo = repo;
        this.permitRepo = permitRepo;
    }

    public List<Resource> list() { return repo.findAllByOrderByNameAsc(); }

    public Resource get(Long id) {
        return repo.findById(id).orElseThrow(() -> new EntityNotFoundException("Resource not found: " + id));
    }

    @Transactional
    public Resource create(ResourceReq req) {
        if (req.parentResourceId() != null && !repo.existsById(req.parentResourceId())) {
            throw new EntityNotFoundException("Parent resource not found: " + req.parentResourceId());
        }
        Resource r = new Resource();
        r.setName(req.name());
        if (req.kind() != null && !req.kind().isBlank()) r.setKind(req.kind());
        r.setCapacity(req.capacity());
        if (req.staffOnly() != null) r.setStaffOnly(req.staffOnly());
        r.setParentResourceId(req.parentResourceId());
        return repo.save(r);
    }

    @Transactional
    public ResourcePermit grantPermit(Long resourceId, Long userId) {
        if (!repo.existsById(resourceId)) throw new EntityNotFoundException("Resource not found: " + resourceId);
        if (permitRepo.existsByResourceIdAndUserId(resourceId, userId)) {
            throw new ConflictException("That permit already exists");
        }
        ResourcePermit p = new ResourcePermit();
        p.setResourceId(resourceId);
        p.setUserId(userId);
        p.setGrantedBy(TenantContext.getUserId());
        return permitRepo.save(p);
    }

    public boolean canAccess(Long resourceId, Long userId, boolean isStaff) {
        Resource r = get(resourceId);
        if (!r.isStaffOnly() || isStaff) return true;
        return permitRepo.existsByResourceIdAndUserId(resourceId, userId);
    }
}
