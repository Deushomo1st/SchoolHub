package com.schoolhub.schoolservice.controller;

import com.schoolhub.schoolservice.dto.Requests.ClassGroupReq;
import com.schoolhub.schoolservice.service.ClassGroupService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/** Teacher-made sub-groups inside a classic SchoolClass. The service enforces that a
 *  teacher only manages groups in classes they actually teach. */
@RestController
@RequestMapping("/api/v1")
public class ClassGroupController {

    private final ClassGroupService service;

    public ClassGroupController(ClassGroupService service) {
        this.service = service;
    }

    @GetMapping("/classes/{classId}/groups")
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER')")
    public ResponseEntity<?> list(@PathVariable Long classId) {
        return ResponseEntity.ok(service.list(classId));
    }

    @PostMapping("/classes/{classId}/groups")
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER')")
    public ResponseEntity<?> create(@PathVariable Long classId, @Valid @RequestBody ClassGroupReq req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(classId, req));
    }

    @PutMapping("/class-groups/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER')")
    public ResponseEntity<?> update(@PathVariable Long id, @Valid @RequestBody ClassGroupReq req) {
        return ResponseEntity.ok(service.update(id, req));
    }

    @DeleteMapping("/class-groups/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER')")
    public ResponseEntity<?> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.ok().build();
    }
}
