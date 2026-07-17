package com.schoolhub.schoolservice.controller;

import com.schoolhub.schoolservice.dto.*;
import com.schoolhub.schoolservice.model.*;
import com.schoolhub.schoolservice.service.LibraryService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/library")
public class LibraryController {

    private final LibraryService libraryService;

    public LibraryController(LibraryService libraryService) {
        this.libraryService = libraryService;
    }

    // ---- Staff management (admin only) ----

    @PostMapping("/staff")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<LibraryStaff> appointStaff(@Valid @RequestBody LibraryStaffReq req) {
        return ResponseEntity.ok(libraryService.appointStaff(req.getUserId()));
    }

    @DeleteMapping("/staff/{staffId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> removeStaff(@PathVariable Long staffId) {
        libraryService.removeStaff(staffId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/staff")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<LibraryStaff>> listStaff() {
        return ResponseEntity.ok(libraryService.listStaff());
    }

    // ---- Student registration (librarian only) ----

    @PostMapping("/students")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<LibraryStudent> registerStudent(@Valid @RequestBody LibraryStudentReq req, Authentication auth) {
        assertLibrarian(auth);
        return ResponseEntity.ok(libraryService.registerStudent(req.getUserId()));
    }

    @PutMapping("/students/{studentId}/suspend")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> suspendStudent(@PathVariable Long studentId, Authentication auth) {
        assertLibrarian(auth);
        libraryService.suspendStudent(studentId);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/students")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<Map<String, Object>>> listStudents(Authentication auth) {
        assertLibrarian(auth);
        return ResponseEntity.ok(libraryService.listStudentsEnriched());
    }

    // ---- Book management (librarian only) ----

    @PostMapping("/books")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Book> createBook(@Valid @RequestBody BookReq req, Authentication auth) {
        assertLibrarian(auth);
        Book b = new Book();
        b.setTitle(req.getTitle());
        b.setAuthor(req.getAuthor());
        b.setIsbn(req.getIsbn());
        b.setDescription(req.getDescription());
        b.setCategory(req.getCategory());
        b.setTotalCopies(req.getTotalCopies() != null ? req.getTotalCopies() : 1);
        b.setAvailableCopies(req.getTotalCopies() != null ? req.getTotalCopies() : 1);
        b.setFilePath(req.getFilePath());
        b.setFileType(req.getFileType());
        b.setFinePerDay(req.getFinePerDay());
        b.setBorrowDays(req.getBorrowDays());
        b.setCoverImage(req.getCoverImage());
        return ResponseEntity.ok(libraryService.createBook(b));
    }

    @PutMapping("/books/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Book> updateBook(@PathVariable Long id, @Valid @RequestBody BookReq req, Authentication auth) {
        assertLibrarian(auth);
        Book b = new Book();
        b.setTitle(req.getTitle());
        b.setAuthor(req.getAuthor());
        b.setIsbn(req.getIsbn());
        b.setDescription(req.getDescription());
        b.setCategory(req.getCategory());
        b.setTotalCopies(req.getTotalCopies());
        b.setAvailableCopies(req.getTotalCopies());
        b.setFilePath(req.getFilePath());
        b.setFileType(req.getFileType());
        b.setFinePerDay(req.getFinePerDay());
        b.setBorrowDays(req.getBorrowDays());
        b.setCoverImage(req.getCoverImage());
        return ResponseEntity.ok(libraryService.updateBook(id, b));
    }

    @DeleteMapping("/books/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> deleteBook(@PathVariable Long id, Authentication auth) {
        assertLibrarian(auth);
        libraryService.deleteBook(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/books")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<Book>> listBooks(@RequestParam(required = false) String query) {
        if (query != null && !query.trim().isEmpty()) {
            return ResponseEntity.ok(libraryService.searchBooks(query));
        }
        return ResponseEntity.ok(libraryService.listBooks());
    }

    @GetMapping("/books/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Book> getBook(@PathVariable Long id) {
        return ResponseEntity.ok(libraryService.getBook(id));
    }

    // ---- Borrow requests (student initiates, librarian approves) ----

    @PostMapping("/borrow-requests")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<BorrowRequest> requestBorrow(@Valid @RequestBody BorrowRequestReq req) {
        return ResponseEntity.ok(libraryService.requestBorrow(req.getLibraryStudentId(), req.getBookId()));
    }

    @PutMapping("/borrow-requests/{requestId}/approve")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<BorrowRequest> approveRequest(@PathVariable Long requestId, Authentication auth) {
        assertLibrarian(auth);
        return ResponseEntity.ok(libraryService.approveRequest(requestId));
    }

    @PutMapping("/borrow-requests/{requestId}/reject")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<BorrowRequest> rejectRequest(@PathVariable Long requestId, @Valid @RequestBody RejectRequestReq req, Authentication auth) {
        assertLibrarian(auth);
        return ResponseEntity.ok(libraryService.rejectRequest(requestId, req.getReason()));
    }

    @GetMapping("/borrow-requests/pending")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<Map<String, Object>>> listPendingRequests(Authentication auth) {
        assertLibrarian(auth);
        return ResponseEntity.ok(libraryService.listPendingRequestsEnriched());
    }

    @GetMapping("/borrow-requests/my")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<List<BorrowRequest>> listMyRequests(@RequestParam Long libraryStudentId) {
        return ResponseEntity.ok(libraryService.listMyRequests(libraryStudentId));
    }

    // ---- Borrow records (return, renew, track) ----

    @PutMapping("/borrow-records/{recordId}/return")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<BorrowRecord> returnBook(@PathVariable Long recordId) {
        return ResponseEntity.ok(libraryService.returnBook(recordId));
    }

    @PutMapping("/borrow-records/{recordId}/renew")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<BorrowRecord> renewBook(@PathVariable Long recordId) {
        return ResponseEntity.ok(libraryService.renewBook(recordId));
    }

    @GetMapping("/borrow-records/my")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<List<BorrowRecord>> listMyBorrows(@RequestParam Long libraryStudentId) {
        return ResponseEntity.ok(libraryService.listMyBorrows(libraryStudentId));
    }

    @GetMapping("/borrow-records/overdue")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<Map<String, Object>>> listOverdue(Authentication auth) {
        assertLibrarian(auth);
        return ResponseEntity.ok(libraryService.listRecordsEnriched(List.of("overdue")));
    }

    /** Every book currently out (active + overdue) — the librarian's Borrowed panel. */
    @GetMapping("/borrow-records/active")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<Map<String, Object>>> listActive(Authentication auth) {
        assertLibrarian(auth);
        return ResponseEntity.ok(libraryService.listRecordsEnriched(List.of("active", "overdue")));
    }

    // ---- Book flags (student/librarian flags, librarian escalates, admin decides) ----

    @PostMapping("/flags")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<BookFlag> flagBook(@Valid @RequestBody BookFlagReq req) {
        return ResponseEntity.ok(libraryService.flagBook(req.getBookId(), req.getFlagType(), req.getComment()));
    }

    @PutMapping("/flags/{flagId}/escalate")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<BookFlag> escalateFlag(@PathVariable Long flagId, Authentication auth) {
        assertLibrarian(auth);
        return ResponseEntity.ok(libraryService.escalateFlag(flagId));
    }

    @PutMapping("/flags/{flagId}/decide")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<BookFlag> decideFlag(@PathVariable Long flagId, @Valid @RequestBody FlagDecisionReq req) {
        return ResponseEntity.ok(libraryService.decideFlag(flagId, req.getDecision()));
    }

    @GetMapping("/flags/escalated")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<Map<String, Object>>> listEscalatedFlags(Authentication auth) {
        assertLibrarian(auth);
        return ResponseEntity.ok(libraryService.listOpenFlagsEnriched());
    }

    @GetMapping("/flags/pending")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<BookFlag>> listPendingDecisions() {
        return ResponseEntity.ok(libraryService.listPendingDecisions());
    }

    // ---- Fine rules (admin configures) ----

    @PutMapping("/fine-rules")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<LibraryFineRule> updateFineRule(@Valid @RequestBody FineRuleReq req) {
        return ResponseEntity.ok(libraryService.updateFineRule(req.getRuleType(), req.getValue()));
    }

    @GetMapping("/fine-rules")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<LibraryFineRule>> listFineRules(Authentication auth) {
        assertLibrarian(auth);
        return ResponseEntity.ok(libraryService.listFineRules());
    }

    // ---- Stats ----

    @GetMapping("/stats")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Map<String, Object>> stats(Authentication auth) {
        assertLibrarian(auth);
        return ResponseEntity.ok(libraryService.stats());
    }

    // ---- Student library info ----

    @GetMapping("/me")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<Map<String, Object>> myLibraryInfo(Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        return ResponseEntity.ok(libraryService.getMyLibraryInfo(userId));
    }

    // ---- Fine payment ----

    @PostMapping("/borrow-records/{recordId}/pay-fine")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> payFine(@PathVariable Long recordId, Authentication auth) {
        libraryService.payFine(recordId, auth);
        return ResponseEntity.ok().build();
    }

    // ---- Helper: verify librarian membership (dedicated LIBRARIAN role OR teacher-librarian in library_staff) ----

    private void assertLibrarian(Authentication auth) {
        // Dedicated librarian: role is LIBRARIAN in the JWT
        if (auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_LIBRARIAN"))) return;
        // Teacher/staff appointed as librarian via library_staff table
        Long userId = (Long) auth.getPrincipal();
        if (libraryService.isLibrarian(userId)) return;
        // Admin also gets library access
        if (auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"))) return;
        throw new AccessDeniedException("Library staff access required");
    }
}
