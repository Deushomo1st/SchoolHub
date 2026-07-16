package com.schoolhub.schoolservice.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
@Entity
@Table(name = "borrow_record")
public class BorrowRecord {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "library_student_id", nullable = false)
    private Long libraryStudentId;

    @Column(name = "book_id", nullable = false)
    private Long bookId;

    @Column(name = "borrow_date", nullable = false)
    private LocalDate borrowDate = LocalDate.now();

    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    @Column(name = "return_date")
    private LocalDate returnDate;

    @Column(name = "renewed_count", nullable = false)
    private Integer renewedCount = 0;

    @Column(name = "fine_charged", nullable = false, precision = 10, scale = 2)
    private BigDecimal fineCharged = BigDecimal.ZERO;

    @Column(name = "fine_paid", nullable = false)
    private Boolean finePaid = false;

    @Column(nullable = false)
    private String status = "active";

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void pre() {
        if (createdAt == null) createdAt = LocalDateTime.now();
        if (borrowDate == null) borrowDate = LocalDate.now();
    }

    public Long getId() { return id; }
    public Long getLibraryStudentId() { return libraryStudentId; }
    public void setLibraryStudentId(Long libraryStudentId) { this.libraryStudentId = libraryStudentId; }
    public Long getBookId() { return bookId; }
    public void setBookId(Long bookId) { this.bookId = bookId; }
    public LocalDate getBorrowDate() { return borrowDate; }
    public void setBorrowDate(LocalDate borrowDate) { this.borrowDate = borrowDate; }
    public LocalDate getDueDate() { return dueDate; }
    public void setDueDate(LocalDate dueDate) { this.dueDate = dueDate; }
    public LocalDate getReturnDate() { return returnDate; }
    public void setReturnDate(LocalDate returnDate) { this.returnDate = returnDate; }
    public Integer getRenewedCount() { return renewedCount; }
    public void setRenewedCount(Integer renewedCount) { this.renewedCount = renewedCount; }
    public BigDecimal getFineCharged() { return fineCharged; }
    public void setFineCharged(BigDecimal fineCharged) { this.fineCharged = fineCharged; }
    public Boolean getFinePaid() { return finePaid; }
    public void setFinePaid(Boolean finePaid) { this.finePaid = finePaid; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
