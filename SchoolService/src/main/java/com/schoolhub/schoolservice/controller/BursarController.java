package com.schoolhub.schoolservice.controller;

import com.schoolhub.schoolservice.model.Bursar;
import com.schoolhub.schoolservice.repository.BursarRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/bursars")
public class BursarController {

    private final BursarRepository repo;

    public BursarController(BursarRepository repo) { this.repo = repo; }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public List<Bursar> list() {
        return repo.findAll();
    }
}
