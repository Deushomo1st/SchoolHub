package com.schoolhub.schoolservice.controller;

import com.schoolhub.schoolservice.dto.StudentRequest;
import com.schoolhub.schoolservice.service.StudentService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/students")
public class StudentController {

    private final StudentService studentService;

    public StudentController(StudentService studentService) {
        this.studentService = studentService;
    }

    // Student PII is staff-only. Students/guardians see their own data via /api/v1/me/*.
    // LIBRARIAN needs the roster to register library members.
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER','BURSAR','LIBRARIAN')")
    public ResponseEntity<?> list() {
        return ResponseEntity.ok(studentService.list());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER','BURSAR')")
    public ResponseEntity<?> get(@PathVariable Long id) {
        return ResponseEntity.ok(studentService.get(id));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER')")
    public ResponseEntity<?> create(@Valid @RequestBody StudentRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(studentService.create(req));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER')")
    public ResponseEntity<?> update(@PathVariable Long id, @Valid @RequestBody StudentRequest req) {
        return ResponseEntity.ok(studentService.update(id, req));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> delete(@PathVariable Long id) {
        studentService.delete(id);
        return ResponseEntity.ok().build();
    }
}
