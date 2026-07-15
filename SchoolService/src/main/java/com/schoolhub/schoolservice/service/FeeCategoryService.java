package com.schoolhub.schoolservice.service;

import com.schoolhub.schoolservice.dto.Requests.FeeCategoryReq;
import com.schoolhub.schoolservice.exception.ConflictException;
import com.schoolhub.schoolservice.model.FeeCategory;
import com.schoolhub.schoolservice.repository.FeeCategoryRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Replaces what used to be a fixed Java Set in FeeService - same 4 defaults, now admin-editable. */
@Service
public class FeeCategoryService {

    private final FeeCategoryRepository repo;

    public FeeCategoryService(FeeCategoryRepository repo) {
        this.repo = repo;
    }

    public List<FeeCategory> list() { return repo.findAllByOrderByNameAsc(); }

    @Transactional
    public FeeCategory create(FeeCategoryReq req) {
        String name = req.name().trim().toLowerCase();
        if (repo.findByNameIgnoreCase(name).isPresent()) {
            throw new ConflictException("Category '" + name + "' already exists");
        }
        FeeCategory c = new FeeCategory();
        c.setName(name);
        return repo.save(c);
    }

    @Transactional
    public void deactivate(Long id) {
        FeeCategory c = repo.findById(id).orElseThrow(() -> new EntityNotFoundException("Category not found: " + id));
        c.setActive(false);
        repo.save(c);
    }

    /** Case-insensitive match against the active set - the validation FeeService used to do inline. */
    public boolean isValid(String name) {
        if (name == null) return false;
        return repo.findByNameIgnoreCase(name).map(FeeCategory::isActive).orElse(false);
    }
}
