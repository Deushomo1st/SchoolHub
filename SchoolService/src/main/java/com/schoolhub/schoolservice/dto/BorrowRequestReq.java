package com.schoolhub.schoolservice.dto;

public class BorrowRequestReq {
    private Long libraryStudentId;
    private Long bookId;
    
    public Long getLibraryStudentId() { return libraryStudentId; }
    public void setLibraryStudentId(Long libraryStudentId) { this.libraryStudentId = libraryStudentId; }
    
    public Long getBookId() { return bookId; }
    public void setBookId(Long bookId) { this.bookId = bookId; }
}
