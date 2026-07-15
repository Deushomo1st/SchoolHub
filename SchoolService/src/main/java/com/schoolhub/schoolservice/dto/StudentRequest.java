package com.schoolhub.schoolservice.dto;

import jakarta.validation.constraints.NotBlank;

import java.time.LocalDate;

public class StudentRequest {
    @NotBlank
    private String admissionNo;
    @NotBlank
    private String firstName;
    @NotBlank
    private String lastName;
    private String gender;
    private LocalDate dateOfBirth;
    private String email;
    private String phone;
    private Long classId;
    private String status;
    /** When present (with email), a STUDENT login is created with this temp password. */
    private String loginPassword;

    public String getAdmissionNo() { return admissionNo; }
    public void setAdmissionNo(String admissionNo) { this.admissionNo = admissionNo; }
    public String getFirstName() { return firstName; }
    public void setFirstName(String firstName) { this.firstName = firstName; }
    public String getLastName() { return lastName; }
    public void setLastName(String lastName) { this.lastName = lastName; }
    public String getGender() { return gender; }
    public void setGender(String gender) { this.gender = gender; }
    public LocalDate getDateOfBirth() { return dateOfBirth; }
    public void setDateOfBirth(LocalDate dateOfBirth) { this.dateOfBirth = dateOfBirth; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public Long getClassId() { return classId; }
    public void setClassId(Long classId) { this.classId = classId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getLoginPassword() { return loginPassword; }
    public void setLoginPassword(String loginPassword) { this.loginPassword = loginPassword; }
}
