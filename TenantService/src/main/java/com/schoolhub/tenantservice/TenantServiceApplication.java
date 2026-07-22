package com.schoolhub.tenantservice;

import com.schoolhub.tenantservice.bootstrap.DbResolver;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class TenantServiceApplication {
    public static void main(String[] args) {
        DbResolver.resolve(args);
        SpringApplication.run(TenantServiceApplication.class, args);
    }
}
