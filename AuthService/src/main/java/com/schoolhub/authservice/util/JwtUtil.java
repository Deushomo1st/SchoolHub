package com.schoolhub.authservice.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

/**
 * SchoolHub JWT helper.
 *
 * Claim shape:
 *   sub           - user id (string)
 *   user_id       - numeric user id
 *   email         - login email
 *   role          - single role string
 *   tenant_id     - school id, or null for PLATFORM_OWNER
 *   tenant_schema - Postgres schema holding this school's data, or null for PLATFORM_OWNER.
 *                   Domain services read this to route every query to the right tenant.
 *   type          - "access" | "refresh"
 *   jti / iat / exp / iss
 */
@Component
public class JwtUtil {

    public static final String TYPE_ACCESS = "access";
    public static final String TYPE_REFRESH = "refresh";

    private final SecretKey signingKey;
    private final int accessExpiryMinutes;
    private final int refreshExpiryDays;
    private final String issuer;

    public JwtUtil(
            @Value("${security.jwt.secret}") String secret,
            @Value("${security.jwt.access-expiry-minutes:60}") int accessExpiryMinutes,
            @Value("${security.jwt.refresh-expiry-days:7}") int refreshExpiryDays,
            @Value("${security.jwt.issuer:schoolhub-auth}") String issuer) {
        this.signingKey = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        this.accessExpiryMinutes = accessExpiryMinutes;
        this.refreshExpiryDays = refreshExpiryDays;
        this.issuer = issuer;
    }

    public String generateAccessToken(Long userId, String email, String role, Long tenantId, String tenantSchema) {
        return build(userId, email, role, tenantId, tenantSchema, TYPE_ACCESS,
                accessExpiryMinutes * 60_000L);
    }

    public String generateRefreshToken(Long userId, String email, String role, Long tenantId, String tenantSchema) {
        return build(userId, email, role, tenantId, tenantSchema, TYPE_REFRESH,
                refreshExpiryDays * 86_400_000L);
    }

    private String build(Long userId, String email, String role, Long tenantId, String tenantSchema,
                         String type, long ttlMillis) {
        Date now = new Date();
        return Jwts.builder()
                .subject(userId.toString())
                .id(UUID.randomUUID().toString())
                .issuer(issuer)
                .claim("user_id", userId)
                .claim("email", email)
                .claim("role", role)
                .claim("tenant_id", tenantId)
                .claim("tenant_schema", tenantSchema)
                .claim("type", type)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + ttlMillis))
                .signWith(signingKey)
                .compact();
    }

    public Claims parseToken(String token) {
        return Jwts.parser().verifyWith(signingKey).build().parseSignedClaims(token).getPayload();
    }

    public Long getUserId(Claims c)        { return Long.parseLong(c.getSubject()); }
    public String getEmail(Claims c)       { return c.get("email", String.class); }
    public String getRole(Claims c)        { return c.get("role", String.class); }
    public Long getTenantId(Claims c)      { return c.get("tenant_id", Long.class); }
    public String getTenantSchema(Claims c){ return c.get("tenant_schema", String.class); }
    public String getType(Claims c)        { return c.get("type", String.class); }
    public String getJti(Claims c)         { return c.getId(); }
    public Instant getExpiry(Claims c)     { return c.getExpiration().toInstant(); }
    public long getAccessExpirySeconds()   { return accessExpiryMinutes * 60L; }
}
