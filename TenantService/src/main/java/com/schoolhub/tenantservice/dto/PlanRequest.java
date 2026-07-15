package com.schoolhub.tenantservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

/** Super-admin creates/edits a subscription plan that schools register on. */
public record PlanRequest(
        @NotBlank String name,
        @NotNull @PositiveOrZero Integer priceNaira,
        @NotNull @PositiveOrZero Integer maxStudents,
        String description) {}
