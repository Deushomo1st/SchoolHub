package com.schoolhub.schoolservice;

import com.schoolhub.schoolservice.bootstrap.DbResolver;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class SchoolServiceApplication {
    public static void main(String[] args) {
        DbResolver.resolve(args);
        SpringApplication.run(SchoolServiceApplication.class, args);
    }
}
