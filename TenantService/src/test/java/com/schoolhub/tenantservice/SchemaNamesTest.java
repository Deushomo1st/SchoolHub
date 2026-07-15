package com.schoolhub.tenantservice;

import com.schoolhub.tenantservice.util.SchemaNames;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** The schema-name guard is a SQL trust boundary - keep it honest. */
class SchemaNamesTest {

    @Test
    void acceptsAndNormalizesGoodCodes() {
        assertEquals("greenfield", SchemaNames.normalize("Greenfield"));
        assertEquals("unity_high", SchemaNames.normalize("  Unity_High  "));
        assertEquals("school2024", SchemaNames.normalize("school2024"));
    }

    @Test
    void rejectsInjectionAndJunk() {
        // anything with quotes, spaces, semicolons, or hyphens must die at the boundary
        for (String bad : new String[]{
                "ab", "1school", "school;drop", "a b", "school-1", "scho\"ol", "x".repeat(41), "" , null}) {
            assertThrows(IllegalArgumentException.class, () -> SchemaNames.normalize(bad), "should reject: " + bad);
        }
    }

    @Test
    void rejectsReserved() {
        for (String r : new String[]{"platform", "public", "pg_catalog", "pg_toast", "information_schema"}) {
            assertThrows(IllegalArgumentException.class, () -> SchemaNames.normalize(r));
        }
    }
}
