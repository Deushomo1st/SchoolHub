package com.schoolhub.schoolservice.exception;

/** Thrown on a uniqueness/duplicate conflict - mapped to HTTP 409. */
public class ConflictException extends RuntimeException {
    public ConflictException(String message) {
        super(message);
    }
}
