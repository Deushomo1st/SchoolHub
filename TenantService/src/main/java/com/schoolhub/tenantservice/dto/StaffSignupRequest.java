package com.schoolhub.tenantservice.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** A staff member self-registers into a school using the school's staff code. */
public record StaffSignupRequest(
        @NotBlank String code,
        @NotBlank @Email String email,
        @NotBlank @Size(min = 8, message = "Password must be at least 8 characters") String password,
        @NotBlank String firstName,
        @NotBlank String lastName,
        String role) {}   // TEACHER (default) or BURSAR; admin approval still required
