package com.schoolhub.tenantservice.model;

import jakarta.persistence.*;

@Entity
@Table(name = "subscription_plan", schema = "platform")
public class SubscriptionPlan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String name;

    @Column(name = "price_naira", nullable = false)
    private Integer priceNaira;

    @Column(name = "max_students", nullable = false)
    private Integer maxStudents;

    @Column(nullable = false)
    private String description;

    public Long getId() { return id; }
    public String getName() { return name; }
    public Integer getPriceNaira() { return priceNaira; }
    public Integer getMaxStudents() { return maxStudents; }
    public String getDescription() { return description; }
    public void setName(String name) { this.name = name; }
    public void setPriceNaira(Integer priceNaira) { this.priceNaira = priceNaira; }
    public void setMaxStudents(Integer maxStudents) { this.maxStudents = maxStudents; }
    public void setDescription(String description) { this.description = description; }
}
