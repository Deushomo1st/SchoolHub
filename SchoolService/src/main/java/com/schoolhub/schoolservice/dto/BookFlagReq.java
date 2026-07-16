package com.schoolhub.schoolservice.dto;

public class BookFlagReq {
    private Long bookId;
    private String flagType;
    private String comment;
    
    public Long getBookId() { return bookId; }
    public void setBookId(Long bookId) { this.bookId = bookId; }
    
    public String getFlagType() { return flagType; }
    public void setFlagType(String flagType) { this.flagType = flagType; }
    
    public String getComment() { return comment; }
    public void setComment(String comment) { this.comment = comment; }
}
