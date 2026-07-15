package com.schoolhub.schoolservice.tenant;

import org.hibernate.context.spi.CurrentTenantIdentifierResolver;

/**
 * Tells Hibernate which school's schema the current query belongs to.
 * Falls back to the platform schema when no tenant is set - a contextless query
 * then fails loudly (no student table there) rather than touching another tenant.
 */
public class CurrentTenantResolver implements CurrentTenantIdentifierResolver<String> {

    private static final String FALLBACK = "platform";

    @Override
    public String resolveCurrentTenantIdentifier() {
        String schema = TenantContext.get();
        return schema != null ? schema : FALLBACK;
    }

    @Override
    public boolean validateExistingCurrentSessions() {
        return false;
    }
}
