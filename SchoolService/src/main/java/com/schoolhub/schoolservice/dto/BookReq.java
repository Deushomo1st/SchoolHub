package com.schoolhub.schoolservice.dto;

import java.math.BigDecimal;

public class BookReq {
    private String title;
    private String author;
    private String isbn;
    private String description;
    private String category;
    private Integer totalCopies;
    private String filePath;
    private String fileType;
    private BigDecimal finePerDay;
    private Integer borrowDays;
    
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    
    public String getAuthor() { return author; }
    public void setAuthor(String author) { this.author = author; }
    
    public String getIsbn() { return isbn; }
    public void setIsbn(String isbn) { this.isbn = isbn; }
    
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    
    public Integer getTotalCopies() { return totalCopies; }
    public void setTotalCopies(Integer totalCopies) { this.totalCopies = totalCopies; }
    
    public String getFilePath() { return filePath; }
    public void setFilePath(String filePath) { this.filePath = filePath; }
    
    public String getFileType() { return fileType; }
    public void setFileType(String fileType) { this.fileType = fileType; }
    
    public BigDecimal getFinePerDay() { return finePerDay; }
    public void setFinePerDay(BigDecimal finePerDay) { this.finePerDay = finePerDay; }
    
    public Integer getBorrowDays() { return borrowDays; }
    public void setBorrowDays(Integer borrowDays) { this.borrowDays = borrowDays; }
}
