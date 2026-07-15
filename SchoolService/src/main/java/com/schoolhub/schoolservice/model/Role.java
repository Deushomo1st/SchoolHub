package com.schoolhub.schoolservice.model;

import jakarta.persistence.*;

@Entity
@Table(name = "role", schema = "platform")
public class Role {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, unique = true)
    private String name;
    @Column(nullable = false)
    private String description;

    public Long getId() { return id; }
    public String getName() { return name; }
}
