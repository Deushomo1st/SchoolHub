package com.schoolhub.schoolservice.controller;

import com.schoolhub.schoolservice.model.LibraryStaff;
import com.schoolhub.schoolservice.repository.LibraryStaffRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/librarians")
public class LibrarianController {

    private final LibraryStaffRepository repo;

    public LibrarianController(LibraryStaffRepository repo) { this.repo = repo; }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public List<LibraryStaff> list() {
        return repo.findByStatus("active");
    }
}
