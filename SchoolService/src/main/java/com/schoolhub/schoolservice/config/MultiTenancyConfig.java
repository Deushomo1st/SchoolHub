package com.schoolhub.schoolservice.config;

import com.schoolhub.schoolservice.tenant.CurrentTenantResolver;
import com.schoolhub.schoolservice.tenant.SchemaMultiTenantConnectionProvider;
import org.hibernate.cfg.AvailableSettings;
import org.hibernate.context.spi.CurrentTenantIdentifierResolver;
import org.hibernate.engine.jdbc.connections.spi.MultiTenantConnectionProvider;
import org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

/** Wires the schema-per-tenant provider + resolver into Hibernate's EntityManagerFactory. */
@Configuration
public class MultiTenancyConfig {

    @Bean
    public MultiTenantConnectionProvider<String> multiTenantConnectionProvider(DataSource dataSource) {
        return new SchemaMultiTenantConnectionProvider(dataSource);
    }

    @Bean
    public CurrentTenantIdentifierResolver<String> currentTenantIdentifierResolver() {
        return new CurrentTenantResolver();
    }

    @Bean
    public HibernatePropertiesCustomizer multiTenancyCustomizer(
            MultiTenantConnectionProvider<String> connectionProvider,
            CurrentTenantIdentifierResolver<String> tenantResolver) {
        return props -> {
            props.put(AvailableSettings.MULTI_TENANT_CONNECTION_PROVIDER, connectionProvider);
            props.put(AvailableSettings.MULTI_TENANT_IDENTIFIER_RESOLVER, tenantResolver);
        };
    }
}
