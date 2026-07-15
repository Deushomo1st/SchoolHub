package com.schoolhub.tenantservice.dto;

import java.util.List;

/** A signup preset. The MVP stores the chosen key; term/grading seeding arrives with AcademicService. */
public record TemplateDto(
        String key,
        String label,
        String description,
        String terms,
        String gradeScale,
        List<String> sampleClasses
) {}
