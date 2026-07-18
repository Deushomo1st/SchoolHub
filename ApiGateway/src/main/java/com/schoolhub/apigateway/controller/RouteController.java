package com.schoolhub.apigateway.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * SchoolHub gateway router. /api/v1/{resource}/** is forwarded to the one service
 * that owns {resource}. The Bearer token rides along untouched; downstream services
 * validate it. Static portal pages are served straight from this app's resources.
 */
@RestController
public class RouteController {

    private final RestTemplate restTemplate;
    private final Map<String, String> serviceUrls;
    private final Map<String, String> resourceToService;

    public RouteController(
            @Value("${services.auth.url:http://localhost:9001}") String authUrl,
            @Value("${services.tenant.url:http://localhost:9002}") String tenantUrl,
            @Value("${services.school.url:http://localhost:9003}") String schoolUrl) {

        // Java HttpClient via JdkClientHttpRequestFactory - the legacy factory can't do PATCH.
        HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(1)).build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(Duration.ofSeconds(10));
        this.restTemplate = new RestTemplate(factory);

        Map<String, String> urls = new HashMap<>();
        urls.put("auth", authUrl);
        urls.put("tenant", tenantUrl);
        urls.put("school", schoolUrl);
        this.serviceUrls = urls;

        // Platform control-plane resources -> auth/tenant; everything else is school data-plane.
        Map<String, String> r2s = new HashMap<>();
        r2s.put("auth", "auth");
        r2s.put("tenants", "tenant");
        r2s.put("activity", "tenant");
        r2s.put("library", "school");
        for (String r : new String[]{
                "students", "teachers", "guardians", "staff", "subjects", "classes", "class-subjects", "class-groups",
                "assessments", "results", "attendance", "events", "invoices", "payments", "fees", "me",
                "cohorts", "enrollments", "org-units", "offerings", "schedule-periods",
                "workflow-requests", "workflow-protests", "notifications", "invites", "people",
                "resources", "sessions", "progression-rules", "credentials", "transcript",
                "financial-settings", "fee-categories", "scholarship-rules", "flags"}) {
            r2s.put(r, "school");
        }
        this.resourceToService = r2s;
    }

    @GetMapping("/health/{serviceKey}")
    public ResponseEntity<?> health(@PathVariable String serviceKey) {
        String base = serviceUrls.get(serviceKey);
        if (base == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Service not found: " + serviceKey));
        }
        try {
            ResponseEntity<String> r = restTemplate.getForEntity(new URI(base + "/health"), String.class);
            return ResponseEntity.status(r.getStatusCode()).body(r.getBody());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of("status", "DOWN", "error", e.getMessage()));
        }
    }

    @RequestMapping(
            value = "/api/v1/{resource}/**",
            method = {RequestMethod.GET, RequestMethod.POST, RequestMethod.PUT, RequestMethod.PATCH, RequestMethod.DELETE}
    )
    public ResponseEntity<?> forwardApi(@PathVariable String resource,
                                        HttpServletRequest request,
                                        @RequestBody(required = false) byte[] body) throws URISyntaxException {
        String serviceKey = resourceToService.get(resource);
        if (serviceKey == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Unknown API resource: " + resource));
        }
        return proxy(serviceUrls.get(serviceKey), request, body);
    }

    private ResponseEntity<?> proxy(String baseUrl, HttpServletRequest request, byte[] body) throws URISyntaxException {
        String qs = request.getQueryString();
        String targetUrl = baseUrl + request.getRequestURI() + (qs != null ? "?" + qs : "");
        HttpMethod method = HttpMethod.valueOf(request.getMethod());

        HttpHeaders headers = new HttpHeaders();
        String ct = request.getContentType();
        if (ct != null) headers.set(HttpHeaders.CONTENT_TYPE, ct);
        String authz = request.getHeader("Authorization");
        if (authz != null) headers.set("Authorization", authz);

        HttpEntity<byte[]> entity = new HttpEntity<>(body, headers);
        try {
            ResponseEntity<byte[]> r = restTemplate.exchange(new URI(targetUrl), method, entity, byte[].class);
            return ResponseEntity.status(r.getStatusCode()).headers(safeHeaders(r.getHeaders())).body(r.getBody());
        } catch (HttpClientErrorException | HttpServerErrorException e) {
            byte[] errBody = e.getResponseBodyAsByteArray();
            MediaType errCt = e.getResponseHeaders() != null ? e.getResponseHeaders().getContentType() : MediaType.APPLICATION_JSON;
            return ResponseEntity.status(e.getStatusCode())
                    .contentType(errCt != null ? errCt : MediaType.APPLICATION_JSON)
                    .contentLength(errBody.length)
                    .body(errBody);
        } catch (ResourceAccessException e) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("message", "Service is not reachable. Start it and try again."));
        }
    }

    private HttpHeaders safeHeaders(HttpHeaders src) {
        HttpHeaders out = new HttpHeaders();
        src.forEach((name, values) -> {
            if (name == null) return;
            String lower = name.toLowerCase();
            if (lower.equals("transfer-encoding") || lower.equals("content-length") || lower.equals("connection")) return;
            out.put(name, values);
        });
        return out;
    }
}
