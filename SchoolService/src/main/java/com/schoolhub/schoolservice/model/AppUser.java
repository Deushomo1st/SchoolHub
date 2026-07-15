package com.schoolhub.schoolservice.model;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * Login account in the shared platform schema. SchoolService writes here only to
 * provision logins for the teachers/students/guardians an ADMIN creates. Fully
 * qualified (@Table schema=platform) so it ignores the per-tenant search_path.
 */
@Entity
@Table(name = "app_user", schema = "platform")
public class AppUser {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
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

    private String phone;

    @Column(name = "role_id", nullable = false)
    private Long roleId;

    @Column(name = "tenant_id")
    private Long tenantId;

    @Column(name = "account_status", nullable = false)
    private String accountStatus = "active";

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist void pre() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
    }

    public Long getId() { return id; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
    public String getFirstName() { return firstName; }
    public void setFirstName(String firstName) { this.firstName = firstName; }
    public String getLastName() { return lastName; }
    public void setLastName(String lastName) { this.lastName = lastName; }
    public void setPhone(String phone) { this.phone = phone; }
    public Long getRoleId() { return roleId; }
    public void setRoleId(Long roleId) { this.roleId = roleId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }
    public void setAccountStatus(String accountStatus) { this.accountStatus = accountStatus; }
}
