package com.schoolhub.tenantservice.service;

import com.schoolhub.tenantservice.dto.TemplateDto;

import java.util.List;

/**
 * Static catalog of signup templates. Shapes one engine into many school types.
 * For now the choice is recorded on the tenant; term/grading/class seeding per
 * template lands with AcademicService.
 */
public final class TemplateCatalog {

    private TemplateCatalog() {}

    public static final List<TemplateDto> TEMPLATES = List.of(
            new TemplateDto("nigerian_secondary", "Nigerian Secondary",
                    "WAEC-style secondary school: 3 terms, A1-F9 grading, JSS/SSS classes.",
                    "3 terms", "WAEC A1-F9",
                    List.of("JSS1", "JSS2", "JSS3", "SSS1", "SSS2", "SSS3")),
            new TemplateDto("primary", "Primary School",
                    "Primary school: 3 terms, percentage grading, Primary 1-6.",
                    "3 terms", "Percentage",
                    List.of("Primary 1", "Primary 2", "Primary 3", "Primary 4", "Primary 5", "Primary 6")),
            new TemplateDto("university", "University / Tertiary",
                    "Tertiary: 2 semesters, GPA letter grades, Year 1-4 levels.",
                    "2 semesters", "GPA A-F",
                    List.of("Year 1", "Year 2", "Year 3", "Year 4")),
            new TemplateDto("generic", "Generic",
                    "Blank slate: 3 terms, percentage grading, no preset classes.",
                    "3 terms", "Percentage",
                    List.of())
    );

    public static boolean isValidKey(String key) {
        return TEMPLATES.stream().anyMatch(t -> t.key().equals(key));
    }
}
