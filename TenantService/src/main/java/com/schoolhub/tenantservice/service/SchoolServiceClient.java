package com.schoolhub.tenantservice.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

/**
 * Calls SchoolService internal endpoints to create staff profile rows
 * after a staff signup is approved in TenantService.
 */
@Component
public class SchoolServiceClient {

    private final RestTemplate rest;
    private final String baseUrl;

    public SchoolServiceClient(RestTemplate restTemplate,
                               @Value("${schoolservice.base-url:http://localhost:9003}") String baseUrl) {
        this.rest = restTemplate;
        this.baseUrl = baseUrl;
    }

    /** Create a profile row in the target table. Internal endpoints are whitelisted — no auth needed. */
    public void createStaffProfile(String role, Map<String, Object> body) {
        String path = switch (role.toUpperCase()) {
            case "TEACHER" -> "/internal/staff-profiles/teacher";
            case "BURSAR" -> "/internal/staff-profiles/bursar";
            case "LIBRARIAN" -> "/internal/staff-profiles/librarian";
            default -> throw new IllegalArgumentException("No profile table for role: " + role);
        };

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

        ResponseEntity<Map> response = rest.exchange(
                baseUrl + path, HttpMethod.POST, entity, Map.class);

        if (!response.getStatusCode().is2xxSuccessful()) {
            throw new RuntimeException("SchoolService returned " + response.getStatusCode()
                    + " for " + path + " — profile creation may have failed");
        }
    }
}
