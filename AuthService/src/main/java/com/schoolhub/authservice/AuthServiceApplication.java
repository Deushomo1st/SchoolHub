package com.schoolhub.authservice;

import com.schoolhub.authservice.bootstrap.BootstrapRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class AuthServiceApplication {
    public static void main(String[] args) {
        BootstrapRunner.run(args);
        SpringApplication.run(AuthServiceApplication.class, args);
    }
}
