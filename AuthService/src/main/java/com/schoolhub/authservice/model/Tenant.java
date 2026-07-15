package com.schoolhub.authservice.model;

import jakarta.persistence.*;

/**
 * Read-only view of a school, used at login to resolve which per-tenant schema
 * the issued JWT should carry. TenantService owns writes to this table.
 */
@Entity
@Table(name = "tenant", schema = "platform")
public class Tenant {

    @Id
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(name = "schema_name", nullable = false)
    private String schemaName;

    @Column(nullable = false)
    private String status;

    public Long getId() { return id; }
    public String getName() { return name; }
    public String getSchemaName() { return schemaName; }
    public String getStatus() { return status; }
}
