package com.schoolhub.tenantservice.model;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/** Written here only to create a school's first ADMIN at signup. AuthService owns the rest. */
@Entity
@Table(name = "app_user", schema = "platform")
public class AppUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String email;

    /** Optional sign-in handle for teachers/staff. */
    @Column(unique = true)
    private String username;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "first_name", nullable = false)
    private String firstName;

    @Column(name = "last_name", nullable = false)
    private String lastName;

    @Column
    private String phone;

    @Column(name = "role_id", nullable = false)
    private Long roleId;

    @Column(name = "tenant_id")
    private Long tenantId;

    @Column(name = "account_status", nullable = false)
    private String accountStatus = "active";

    @Column(name = "avatar", columnDefinition = "text")
    private String avatar;

    /** Admin-given staff title shown platform-wide (e.g. "Head of Sciences"), or NULL. */
    @Column(name = "staff_title")
    private String staffTitle;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
    }

    public Long getId() { return id; }
    public String getEmail() { return email; }
    public String getUsername() { return username; }
    public String getFirstName() { return firstName; }
    public String getLastName() { return lastName; }
    public Long getRoleId() { return roleId; }
    public Long getTenantId() { return tenantId; }
    public String getAccountStatus() { return accountStatus; }
    public String getAvatar() { return avatar; }
    public String getStaffTitle() { return staffTitle; }
    public void setStaffTitle(String staffTitle) { this.staffTitle = staffTitle; }
    public void setEmail(String email) { this.email = email; }
    public void setUsername(String username) { this.username = username; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
    public void setFirstName(String firstName) { this.firstName = firstName; }
    public void setLastName(String lastName) { this.lastName = lastName; }
    public void setPhone(String phone) { this.phone = phone; }
    public void setRoleId(Long roleId) { this.roleId = roleId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }
    public void setAccountStatus(String accountStatus) { this.accountStatus = accountStatus; }
}
