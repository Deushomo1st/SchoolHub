package com.schoolhub.tenantservice.util;

import java.util.Set;
import java.util.regex.Pattern;

/**
 * Turns a user-supplied school code into a safe Postgres schema identifier.
 * This is a SQL-injection trust boundary: the result is interpolated into
 * CREATE SCHEMA, so the strict allow-list pattern below is the real guard.
 */
public final class SchemaNames {

    private SchemaNames() {}

    // start with a letter; 3-40 chars total; lowercase letters, digits, underscore only
    private static final Pattern VALID = Pattern.compile("^[a-z][a-z0-9_]{2,39}$");

    private static final Set<String> RESERVED =
            Set.of("platform", "public", "information_schema", "pg_catalog", "pg_toast");

    /** @return the normalized, validated schema name. @throws IllegalArgumentException if unusable. */
    public static String normalize(String code) {
        if (code == null) throw new IllegalArgumentException("School code is required");
        String s = code.trim().toLowerCase();
        if (!VALID.matcher(s).matches()) {
            throw new IllegalArgumentException(
                    "School code must be 3-40 characters, start with a letter, and use only a-z, 0-9, underscore");
        }
        if (s.startsWith("pg_") || RESERVED.contains(s)) {
            throw new IllegalArgumentException("School code '" + s + "' is reserved");
        }
        return s;
    }
}
