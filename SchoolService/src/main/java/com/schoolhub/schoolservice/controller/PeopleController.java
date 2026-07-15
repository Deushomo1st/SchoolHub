package com.schoolhub.schoolservice.controller;

import com.schoolhub.schoolservice.dto.Requests.GuardianReq;
import com.schoolhub.schoolservice.dto.Requests.TeacherReq;
import com.schoolhub.schoolservice.service.PeopleService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class PeopleController {

    private final PeopleService people;

    public PeopleController(PeopleService people) {
        this.people = people;
    }

    // ---- Teachers ---- (staff PII: emails, phones, staff numbers -> staff only)
    @GetMapping("/teachers")
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER','BURSAR')")
    public ResponseEntity<?> listTeachers() { return ResponseEntity.ok(people.listTeachers()); }

    @GetMapping("/teachers/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER','BURSAR')")
    public ResponseEntity<?> getTeacher(@PathVariable Long id) { return ResponseEntity.ok(people.getTeacher(id)); }

    @PostMapping("/teachers")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> createTeacher(@Valid @RequestBody TeacherReq req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(people.createTeacher(req));
    }

    @PutMapping("/teachers/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> updateTeacher(@PathVariable Long id, @Valid @RequestBody TeacherReq req) {
        return ResponseEntity.ok(people.updateTeacher(id, req));
    }

    @DeleteMapping("/teachers/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> deleteTeacher(@PathVariable Long id) {
        people.deleteTeacher(id);
        return ResponseEntity.ok().build();
    }

    // ---- Guardians ----
    @GetMapping("/guardians")
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER')")
    public ResponseEntity<?> listGuardians() { return ResponseEntity.ok(people.listGuardians()); }

    @PostMapping("/guardians")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> createGuardian(@Valid @RequestBody GuardianReq req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(people.createGuardian(req));
    }

    @DeleteMapping("/guardians/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> deleteGuardian(@PathVariable Long id) {
        people.deleteGuardian(id);
        return ResponseEntity.ok().build();
    }

    // ---- Handle search (e.g. a guardian finding a ward before linking) ----
    @GetMapping("/people/search")
    public ResponseEntity<?> searchByHandle(@RequestParam String handle) {
        return ResponseEntity.ok(people.searchByHandle(handle));
    }
}
