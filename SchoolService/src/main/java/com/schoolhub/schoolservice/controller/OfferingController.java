package com.schoolhub.schoolservice.controller;

import com.schoolhub.schoolservice.dto.Requests.OfferingReq;
import com.schoolhub.schoolservice.service.OfferingService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/offerings")
public class OfferingController {

    private final OfferingService service;

    public OfferingController(OfferingService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<?> list() { return ResponseEntity.ok(service.list()); }

    @GetMapping("/roots")
    public ResponseEntity<?> roots() { return ResponseEntity.ok(service.roots()); }

    @GetMapping("/{id}")
    public ResponseEntity<?> get(@PathVariable Long id) { return ResponseEntity.ok(service.get(id)); }

    @GetMapping("/{id}/children")
    public ResponseEntity<?> children(@PathVariable Long id) { return ResponseEntity.ok(service.children(id)); }

    @GetMapping("/{id}/ancestors")
    public ResponseEntity<?> ancestors(@PathVariable Long id) { return ResponseEntity.ok(service.ancestors(id)); }

    // Direct creation is Institution-Owner-level for now; a Teacher's "propose, Head confirms"
    // path arrives in Phase 3/4 on top of this same create(), not as a rewrite of it.
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> create(@Valid @RequestBody OfferingReq req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(req));
    }

    // ---- Cross-listing (one Offering, many Org Units) ----

    @GetMapping("/{id}/org-units")
    public ResponseEntity<?> crossListings(@PathVariable Long id) {
        return ResponseEntity.ok(service.crossListings(id));
    }

    @PostMapping("/{id}/org-units/{orgUnitId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> addCrossListing(@PathVariable Long id, @PathVariable Long orgUnitId) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.addCrossListing(id, orgUnitId));
    }

    @DeleteMapping("/{id}/org-units/{orgUnitId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> removeCrossListing(@PathVariable Long id, @PathVariable Long orgUnitId) {
        service.removeCrossListing(id, orgUnitId);
        return ResponseEntity.ok().build();
    }

    // ---- Prerequisites ----

    @GetMapping("/{id}/prerequisites")
    public ResponseEntity<?> prerequisites(@PathVariable Long id) {
        return ResponseEntity.ok(service.prerequisites(id));
    }

    @PostMapping("/{id}/prerequisites/{prerequisiteId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> addPrerequisite(@PathVariable Long id, @PathVariable Long prerequisiteId) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.addPrerequisite(id, prerequisiteId));
    }

    @DeleteMapping("/{id}/prerequisites/{prerequisiteId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> removePrerequisite(@PathVariable Long id, @PathVariable Long prerequisiteId) {
        service.removePrerequisite(id, prerequisiteId);
        return ResponseEntity.ok().build();
    }
}
