package com.schoolhub.schoolservice.tenant;

import org.hibernate.engine.jdbc.connections.spi.MultiTenantConnectionProvider;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.regex.Pattern;

/**
 * Schema-per-tenant connection provider. Hands out pooled connections with
 * search_path pointed at the active school's schema, and resets it on release so
 * the pooled connection never leaks one tenant's schema to another.
 */
public class SchemaMultiTenantConnectionProvider implements MultiTenantConnectionProvider<String> {

    // Defense in depth: the value already came from our validated registry, but it
    // is interpolated into SET search_path, so re-check the identifier shape here.
    private static final Pattern SAFE = Pattern.compile("^[a-z][a-z0-9_]{2,39}$");

    private final DataSource dataSource;

    public SchemaMultiTenantConnectionProvider(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public Connection getAnyConnection() throws SQLException {
        return dataSource.getConnection();
    }

    @Override
    public void releaseAnyConnection(Connection connection) throws SQLException {
        connection.close();
    }

    @Override
    public Connection getConnection(String tenant) throws SQLException {
        if (tenant == null || !SAFE.matcher(tenant).matches()) {
            throw new SQLException("Refusing to route to unsafe tenant schema: " + tenant);
        }
        Connection connection = getAnyConnection();
        try (Statement st = connection.createStatement()) {
            st.execute("SET search_path TO \"" + tenant + "\", public");
        }
        return connection;
    }

    @Override
    public void releaseConnection(String tenant, Connection connection) throws SQLException {
        try (Statement st = connection.createStatement()) {
            st.execute("SET search_path TO platform, public");
        } finally {
            connection.close();
        }
    }

    @Override
    public boolean supportsAggressiveRelease() {
        return false;
    }

    @Override
    public boolean isUnwrappableAs(Class<?> unwrapType) {
        return false;
    }

    @Override
    public <T> T unwrap(Class<T> unwrapType) {
        return null;
    }
}
