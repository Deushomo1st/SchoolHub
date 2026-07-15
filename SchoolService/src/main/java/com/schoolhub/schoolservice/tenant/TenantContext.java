package com.schoolhub.schoolservice.tenant;

/**
 * Per-request identity for the current thread, set by the JWT filter and cleared
 * when the request ends:
 *   - schema   : the active school's Postgres schema (read by Hibernate's tenant resolver)
 *   - userId   : the caller's platform.app_user id (who am I)
 *   - tenantId : the caller's school id (for stamping new rows / new logins)
 */
public final class TenantContext {

    private static final ThreadLocal<String> SCHEMA = new ThreadLocal<>();
    private static final ThreadLocal<Long> USER_ID = new ThreadLocal<>();
    private static final ThreadLocal<Long> TENANT_ID = new ThreadLocal<>();

    private TenantContext() {}

    public static void set(String schema) { SCHEMA.set(schema); }
    public static String get() { return SCHEMA.get(); }

    public static void setUserId(Long id) { USER_ID.set(id); }
    public static Long getUserId() { return USER_ID.get(); }

    public static void setTenantId(Long id) { TENANT_ID.set(id); }
    public static Long getTenantId() { return TENANT_ID.get(); }

    public static void clear() {
        SCHEMA.remove();
        USER_ID.remove();
        TENANT_ID.remove();
    }
}
