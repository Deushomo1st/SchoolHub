package com.schoolhub.schoolservice.service;

import com.schoolhub.schoolservice.exception.ConflictException;
import com.schoolhub.schoolservice.model.*;
import com.schoolhub.schoolservice.repository.*;
import com.schoolhub.schoolservice.tenant.TenantContext;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
public class LibraryService {

    private final LibraryStaffRepository staffRepo;
    private final LibraryStudentRepository studentRepo;
    private final BookRepository bookRepo;
    private final BorrowRequestRepository requestRepo;
    private final BorrowRecordRepository recordRepo;
    private final BookFlagRepository flagRepo;
    private final LibraryFineRuleRepository fineRuleRepo;
    private final NotificationService notificationService;
    private final AppUserRepository appUserRepo;
    private final AuditRecorder audit;

    private static final String CODE_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    public LibraryService(LibraryStaffRepository staffRepo, LibraryStudentRepository studentRepo,
                          BookRepository bookRepo, BorrowRequestRepository requestRepo,
                          BorrowRecordRepository recordRepo, BookFlagRepository flagRepo,
                          LibraryFineRuleRepository fineRuleRepo,
                          NotificationService notificationService, AppUserRepository appUserRepo,
                          AuditRecorder audit) {
        this.staffRepo = staffRepo;
        this.studentRepo = studentRepo;
        this.bookRepo = bookRepo;
        this.requestRepo = requestRepo;
        this.recordRepo = recordRepo;
        this.flagRepo = flagRepo;
        this.fineRuleRepo = fineRuleRepo;
        this.notificationService = notificationService;
        this.appUserRepo = appUserRepo;
        this.audit = audit;
    }

    public boolean isLibrarian(Long userId) {
        return staffRepo.existsByUserId(userId);
    }

    // ---- Staff management (admin only) ----

    @Transactional
    public LibraryStaff appointStaff(Long userId) {
        if (staffRepo.existsByUserId(userId)) {
            throw new ConflictException("User is already library staff");
        }
        LibraryStaff s = new LibraryStaff();
        s.setUserId(userId);
        s = staffRepo.save(s);
        audit.record("LIBRARY_STAFF_APPOINTED", "userId=" + userId);
        return s;
    }

    @Transactional
    public void removeStaff(Long staffId) {
        LibraryStaff s = staffRepo.findById(staffId)
                .orElseThrow(() -> new EntityNotFoundException("Library staff not found"));
        staffRepo.delete(s);
        audit.record("LIBRARY_STAFF_REMOVED", "staffId=" + staffId);
    }

    public List<LibraryStaff> listStaff() {
        return staffRepo.findByStatus("active");
    }

    // ---- Student registration (librarian only) ----

    @Transactional
    public LibraryStudent registerStudent(Long userId) {
        if (studentRepo.existsByUserId(userId)) {
            throw new ConflictException("Student is already registered in the library");
        }
        LibraryStudent ls = new LibraryStudent();
        ls.setUserId(userId);
        ls.setLibraryCode(generateCode());
        ls = studentRepo.save(ls);
        audit.record("LIBRARY_STUDENT_REGISTERED", "userId=" + userId + " code=" + ls.getLibraryCode());
        return ls;
    }

    @Transactional
    public void suspendStudent(Long studentId) {
        LibraryStudent ls = studentRepo.findById(studentId)
                .orElseThrow(() -> new EntityNotFoundException("Library student not found"));
        ls.setStatus("suspended");
        studentRepo.save(ls);
        audit.record("LIBRARY_STUDENT_SUSPENDED", "studentId=" + studentId);
    }

    public List<LibraryStudent> listStudents() {
        return studentRepo.findAll();
    }

    public Optional<LibraryStudent> findStudentByUserId(Long userId) {
        return studentRepo.findByUserId(userId);
    }

    // ---- Book management (librarian only) ----

    @Transactional
    public Book createBook(Book b) {
        b.setUploadedBy(TenantContext.getUserId());
        b = bookRepo.save(b);
        audit.record("BOOK_CREATED", "id=" + b.getId() + " title=" + b.getTitle());
        return b;
    }

    @Transactional
    public Book updateBook(Long id, Book updated) {
        Book b = bookRepo.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Book not found"));
        b.setTitle(updated.getTitle());
        b.setAuthor(updated.getAuthor());
        b.setIsbn(updated.getIsbn());
        b.setDescription(updated.getDescription());
        b.setCategory(updated.getCategory());
        b.setTotalCopies(updated.getTotalCopies());
        b.setAvailableCopies(updated.getAvailableCopies());
        b.setFilePath(updated.getFilePath());
        b.setFileType(updated.getFileType());
        b.setFinePerDay(updated.getFinePerDay());
        b.setBorrowDays(updated.getBorrowDays());
        b = bookRepo.save(b);
        audit.record("BOOK_UPDATED", "id=" + id);
        return b;
    }

    @Transactional
    public void deleteBook(Long id) {
        Book b = bookRepo.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Book not found"));
        bookRepo.delete(b);
        audit.record("BOOK_DELETED", "id=" + id + " title=" + b.getTitle());
    }

    public List<Book> listBooks() {
        return bookRepo.findAllByOrderByCreatedAtDesc();
    }

    public List<Book> searchBooks(String query) {
        return bookRepo.findByTitleContainingIgnoreCaseOrAuthorContainingIgnoreCase(query, query);
    }

    public Book getBook(Long id) {
        return bookRepo.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Book not found"));
    }

    // ---- Borrow requests (student initiates, librarian approves) ----

    @Transactional
    public BorrowRequest requestBorrow(Long libraryStudentId, Long bookId) {
        Book book = bookRepo.findById(bookId)
                .orElseThrow(() -> new EntityNotFoundException("Book not found"));
        if (book.getAvailableCopies() <= 0) {
            throw new ConflictException("No copies available");
        }
        // Check max books rule
        LibraryFineRule maxBooksRule = fineRuleRepo.findByRuleType("max_books_per_student").orElse(null);
        if (maxBooksRule != null) {
            long activeCount = recordRepo.findByLibraryStudentIdAndStatus(libraryStudentId, "active").size()
                    + recordRepo.findByLibraryStudentIdAndStatus(libraryStudentId, "overdue").size();
            if (activeCount >= maxBooksRule.getValue().intValue()) {
                throw new ConflictException("Maximum books per student reached (" + maxBooksRule.getValue().intValue() + ")");
            }
        }
        // Check if already has pending request for same book
        List<BorrowRequest> existing = requestRepo.findByLibraryStudentIdAndStatus(libraryStudentId, "pending");
        if (existing.stream().anyMatch(r -> r.getBookId().equals(bookId))) {
            throw new ConflictException("Already have a pending request for this book");
        }
        BorrowRequest req = new BorrowRequest();
        req.setLibraryStudentId(libraryStudentId);
        req.setBookId(bookId);
        req = requestRepo.save(req);
        audit.record("BORROW_REQUESTED", "studentId=" + libraryStudentId + " bookId=" + bookId);
        // Notify all librarians about the new request
        for (LibraryStaff staff : staffRepo.findByStatus("active")) {
            notificationService.notify(staff.getUserId(), "library_request", "New borrow request",
                    book.getTitle() + " — awaiting approval", "library", bookId);
        }
        return req;
    }

    @Transactional
    public BorrowRequest approveRequest(Long requestId) {
        BorrowRequest req = requestRepo.findById(requestId)
                .orElseThrow(() -> new EntityNotFoundException("Borrow request not found"));
        if (!"pending".equals(req.getStatus())) {
            throw new ConflictException("Request is not pending");
        }
        Book book = bookRepo.findById(req.getBookId())
                .orElseThrow(() -> new EntityNotFoundException("Book not found"));
        if (book.getAvailableCopies() <= 0) {
            throw new ConflictException("No copies available");
        }
        // Decrement available copies
        book.setAvailableCopies(book.getAvailableCopies() - 1);
        bookRepo.save(book);
        // Create borrow record
        LibraryFineRule maxDaysRule = fineRuleRepo.findByRuleType("max_borrow_days").orElse(null);
        int borrowDays = book.getBorrowDays() != null ? book.getBorrowDays()
                : (maxDaysRule != null ? maxDaysRule.getValue().intValue() : 14);
        BorrowRecord rec = new BorrowRecord();
        rec.setLibraryStudentId(req.getLibraryStudentId());
        rec.setBookId(req.getBookId());
        rec.setBorrowDate(LocalDate.now());
        rec.setDueDate(LocalDate.now().plusDays(borrowDays));
        recordRepo.save(rec);
        // Update request status
        req.setStatus("approved");
        req.setDecidedBy(TenantContext.getUserId());
        req.setDecidedAt(LocalDateTime.now());
        req = requestRepo.save(req);
        audit.record("BORROW_APPROVED", "requestId=" + requestId + " bookId=" + book.getId());
        // Notify the student
        LibraryStudent student = studentRepo.findById(req.getLibraryStudentId()).orElse(null);
        if (student != null) {
            notificationService.notify(student.getUserId(), "library_approved", "Borrow approved",
                    book.getTitle() + " — due " + LocalDate.now().plusDays(borrowDays), "library", book.getId());
        }
        return req;
    }

    @Transactional
    public BorrowRequest rejectRequest(Long requestId, String reason) {
        BorrowRequest req = requestRepo.findById(requestId)
                .orElseThrow(() -> new EntityNotFoundException("Borrow request not found"));
        if (!"pending".equals(req.getStatus())) {
            throw new ConflictException("Request is not pending");
        }
        req.setStatus("rejected");
        req.setRejectionReason(reason);
        req.setDecidedBy(TenantContext.getUserId());
        req.setDecidedAt(LocalDateTime.now());
        req = requestRepo.save(req);
        audit.record("BORROW_REJECTED", "requestId=" + requestId + " reason=" + reason);
        // Notify the student
        LibraryStudent student = studentRepo.findById(req.getLibraryStudentId()).orElse(null);
        if (student != null) {
            notificationService.notify(student.getUserId(), "library_rejected", "Borrow request declined",
                    "Your request was declined" + (reason != null ? ": " + reason : ""), "library", req.getBookId());
        }
        return req;
    }

    public List<BorrowRequest> listPendingRequests() {
        return requestRepo.findByStatus("pending");
    }

    public List<BorrowRequest> listMyRequests(Long libraryStudentId) {
        return requestRepo.findByLibraryStudentIdOrderByRequestedAtDesc(libraryStudentId);
    }

    // ---- Borrow records (return, renew, track) ----

    @Transactional
    public BorrowRecord returnBook(Long recordId) {
        BorrowRecord rec = recordRepo.findById(recordId)
                .orElseThrow(() -> new EntityNotFoundException("Borrow record not found"));
        if ("returned".equals(rec.getStatus())) {
            throw new ConflictException("Already returned");
        }
        Book book = bookRepo.findById(rec.getBookId())
                .orElseThrow(() -> new EntityNotFoundException("Book not found"));
        // Calculate fine if overdue
        BigDecimal fine = calculateFine(rec, book);
        rec.setFineCharged(fine);
        rec.setReturnDate(LocalDate.now());
        rec.setStatus("returned");
        rec = recordRepo.save(rec);
        // Increment available copies
        book.setAvailableCopies(book.getAvailableCopies() + 1);
        bookRepo.save(book);
        audit.record("BOOK_RETURNED", "recordId=" + recordId + " bookId=" + book.getId() + " fine=" + fine);
        return rec;
    }

    @Transactional
    public BorrowRecord renewBook(Long recordId) {
        BorrowRecord rec = recordRepo.findById(recordId)
                .orElseThrow(() -> new EntityNotFoundException("Borrow record not found"));
        if ("returned".equals(rec.getStatus())) {
            throw new ConflictException("Cannot renew returned book");
        }
        LibraryFineRule maxDaysRule = fineRuleRepo.findByRuleType("max_borrow_days").orElse(null);
        int borrowDays = maxDaysRule != null ? maxDaysRule.getValue().intValue() : 14;
        rec.setDueDate(rec.getDueDate().plusDays(borrowDays));
        rec.setRenewedCount(rec.getRenewedCount() + 1);
        rec = recordRepo.save(rec);
        audit.record("BOOK_RENEWED", "recordId=" + recordId + " newDueDate=" + rec.getDueDate());
        return rec;
    }

    public List<BorrowRecord> listMyBorrows(Long libraryStudentId) {
        return recordRepo.findByLibraryStudentIdOrderByBorrowDateDesc(libraryStudentId);
    }

    public List<BorrowRecord> listOverdue() {
        return recordRepo.findByStatus("overdue");
    }

    // ---- Book flags (student/librarian flags, librarian escalates, admin decides) ----

    @Transactional
    public BookFlag flagBook(Long bookId, String flagType, String comment) {
        Book book = bookRepo.findById(bookId)
                .orElseThrow(() -> new EntityNotFoundException("Book not found"));
        BookFlag f = new BookFlag();
        f.setBookId(bookId);
        f.setFlaggedBy(TenantContext.getUserId());
        f.setFlagType(flagType);
        f.setComment(comment);
        f = flagRepo.save(f);
        audit.record("BOOK_FLAGGED", "bookId=" + bookId + " type=" + flagType);
        return f;
    }

    @Transactional
    public BookFlag escalateFlag(Long flagId) {
        BookFlag f = flagRepo.findById(flagId)
                .orElseThrow(() -> new EntityNotFoundException("Book flag not found"));
        f.setEscalated(true);
        f = flagRepo.save(f);
        audit.record("BOOK_FLAG_ESCALATED", "flagId=" + flagId);
        // Notify all admins about the escalated flag
        Long tenantId = TenantContext.getTenantId();
        if (tenantId != null) {
            List<AppUser> admins = appUserRepo.findByRoleIdAndTenantId(2L, tenantId); // role_id 2 = ADMIN
            Book book = bookRepo.findById(f.getBookId()).orElse(null);
            String title = book != null ? book.getTitle() : "Unknown book";
            for (AppUser admin : admins) {
                notificationService.notify(admin.getId(), "library_flag_escalated", "Book flag escalated",
                        title + " — needs your review", "library", f.getBookId());
            }
        }
        return f;
    }

    @Transactional
    public BookFlag decideFlag(Long flagId, String decision) {
        BookFlag f = flagRepo.findById(flagId)
                .orElseThrow(() -> new EntityNotFoundException("Book flag not found"));
        f.setAdminDecision(decision);
        f.setDecidedBy(TenantContext.getUserId());
        f.setDecidedAt(LocalDateTime.now());
        f = flagRepo.save(f);
        if ("remove".equals(decision)) {
            Book book = bookRepo.findById(f.getBookId()).orElse(null);
            if (book != null) {
                bookRepo.delete(book);
                audit.record("BOOK_REMOVED_BY_ADMIN", "bookId=" + book.getId());
            }
        }
        audit.record("BOOK_FLAG_DECIDED", "flagId=" + flagId + " decision=" + decision);
        return f;
    }

    public List<BookFlag> listEscalatedFlags() {
        return flagRepo.findByEscalatedTrue();
    }

    public List<BookFlag> listPendingDecisions() {
        return flagRepo.findByAdminDecisionIsNullAndEscalatedTrue();
    }

    // ---- Fine rules (admin configures) ----

    @Transactional
    public LibraryFineRule updateFineRule(String ruleType, BigDecimal value) {
        LibraryFineRule rule = fineRuleRepo.findByRuleType(ruleType)
                .orElseThrow(() -> new EntityNotFoundException("Fine rule not found"));
        rule.setValue(value);
        rule.setUpdatedBy(TenantContext.getUserId());
        rule = fineRuleRepo.save(rule);
        audit.record("FINE_RULE_UPDATED", "type=" + ruleType + " value=" + value);
        return rule;
    }

    public List<LibraryFineRule> listFineRules() {
        return fineRuleRepo.findAll();
    }

    // ---- Helpers ----

    private String generateCode() {
        StringBuilder sb = new StringBuilder(6);
        for (int i = 0; i < 6; i++) {
            sb.append(CODE_CHARS.charAt(RANDOM.nextInt(CODE_CHARS.length())));
        }
        return sb.toString();
    }

    private BigDecimal calculateFine(BorrowRecord rec, Book book) {
        LocalDate today = LocalDate.now();
        if (!today.isAfter(rec.getDueDate())) {
            return BigDecimal.ZERO;
        }
        long overdueDays = ChronoUnit.DAYS.between(rec.getDueDate(), today);
        BigDecimal finePerDay = book.getFinePerDay() != null ? book.getFinePerDay()
                : fineRuleRepo.findByRuleType("fine_per_day").map(LibraryFineRule::getValue).orElse(BigDecimal.valueOf(50));
        return finePerDay.multiply(BigDecimal.valueOf(overdueDays));
    }

    // ---- Stats ----

    public Map<String, Object> stats() {
        Map<String, Object> s = new HashMap<>();
        s.put("totalBooks", bookRepo.count());
        s.put("activeBorrows", recordRepo.countByStatus("active"));
        s.put("overdueBorrows", recordRepo.countByStatus("overdue"));
        s.put("totalStudents", studentRepo.count());
        s.put("pendingRequests", requestRepo.countByStatus("pending"));
        return s;
    }

    // ---- Student library info ----

    public Map<String, Object> getMyLibraryInfo(Long userId) {
        LibraryStudent student = studentRepo.findByUserId(userId)
                .orElseThrow(() -> new EntityNotFoundException("Library student not found"));
        
        Map<String, Object> info = new HashMap<>();
        info.put("libraryCode", student.getLibraryCode());
        info.put("activeBorrows", recordRepo.countActiveBorrows(student.getId()));
        info.put("totalFines", recordRepo.sumUnpaidFines(student.getId()));
        info.put("borrowHistory", recordRepo.findByLibraryStudentIdOrderByBorrowDateDesc(student.getId()));
        
        return info;
    }

    // ---- Fine payment ----

    @Transactional
    public void payFine(Long recordId, org.springframework.security.core.Authentication auth) {
        BorrowRecord record = recordRepo.findById(recordId)
                .orElseThrow(() -> new EntityNotFoundException("Borrow record not found"));

        if (record.getFinePaid()) {
            throw new ConflictException("Fine already paid");
        }

        if (record.getFineCharged().compareTo(BigDecimal.ZERO) == 0) {
            throw new ConflictException("No fine to pay");
        }

        // Check permission: either the student who borrowed, or a librarian
        LibraryStudent student = studentRepo.findById(record.getLibraryStudentId())
                .orElseThrow(() -> new EntityNotFoundException("Library student not found"));

        boolean isStudent = auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_STUDENT"));
        boolean isLibrarian = auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_LIBRARIAN") || a.getAuthority().equals("ROLE_ADMIN"));

        if (isStudent && !student.getUserId().equals(auth.getPrincipal())) {
            throw new org.springframework.security.access.AccessDeniedException("Cannot pay fine for another student");
        }

        if (!isStudent && !isLibrarian) {
            throw new org.springframework.security.access.AccessDeniedException("Only students or librarians can pay fines");
        }

        record.setFinePaid(true);
        recordRepo.save(record);
    }

    // ---- Scheduled: detect overdue books ----

    @org.springframework.scheduling.annotation.Scheduled(cron = "0 0 0 * * *") // Midnight daily
    @Transactional
    public void detectOverdueBooks() {
        LocalDate today = LocalDate.now();
        List<BorrowRecord> activeRecords = recordRepo.findByStatus("active");
        
        for (BorrowRecord record : activeRecords) {
            if (record.getDueDate().isBefore(today)) {
                // Mark as overdue
                record.setStatus("overdue");
                recordRepo.save(record);
                
                // Calculate and set fine
                Book book = bookRepo.findById(record.getBookId()).orElse(null);
                if (book != null) {
                    BigDecimal fine = calculateFine(record, book);
                    record.setFineCharged(fine);
                    recordRepo.save(record);
                    
                    // Notify student
                    LibraryStudent student = studentRepo.findById(record.getLibraryStudentId()).orElse(null);
                    if (student != null) {
                        notificationService.notify(student.getUserId(), "library_overdue", "Book overdue",
                                book.getTitle() + " — please return it. Fine: " + fine, "library", book.getId());
                    }
                }
            }
        }
    }
}
