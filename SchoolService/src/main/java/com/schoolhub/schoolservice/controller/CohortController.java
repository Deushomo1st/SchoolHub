package com.schoolhub.schoolservice.controller;

import com.schoolhub.schoolservice.dto.Requests.CohortReq;
import com.schoolhub.schoolservice.dto.Requests.GroupReq;
import com.schoolhub.schoolservice.service.CohortService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/cohorts")
public class CohortController {

    private final CohortService service;

    public CohortController(CohortService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<?> list() { return ResponseEntity.ok(service.list()); }

    @GetMapping("/{id}")
    public ResponseEntity<?> get(@PathVariable Long id) { return ResponseEntity.ok(service.get(id)); }

    // Formal Cohort creation ("grouping of classes") stays Admin/Mod, same as SchoolClass today.
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> create(@Valid @RequestBody CohortReq req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(req));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.ok().build();
    }

    // ---- Groups (nest inside a Cohort: sub-groupings of learners are a Teacher's call) ----

    @GetMapping("/{cohortId}/groups")
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER')")
    public ResponseEntity<?> listGroups(@PathVariable Long cohortId) {
        return ResponseEntity.ok(service.listGroups(cohortId));
    }

    @PostMapping("/groups")
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER')")
    public ResponseEntity<?> createGroup(@Valid @RequestBody GroupReq req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.createGroup(req));
    }
}
