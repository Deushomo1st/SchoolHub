package com.schoolhub.schoolservice.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

/** Downstream verify-only JWT helper. */
@Component
public class JwtUtil {

    public static final String TYPE_ACCESS = "access";

    private final SecretKey signingKey;

    public JwtUtil(@Value("${security.jwt.secret}") String secret) {
        this.signingKey = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    }

    public Claims parseToken(String token) {
        return Jwts.parser().verifyWith(signingKey).build().parseSignedClaims(token).getPayload();
    }

    public Long getUserId(Claims c)         { return Long.parseLong(c.getSubject()); }
    public String getRole(Claims c)         { return c.get("role", String.class); }
    public Long getTenantId(Claims c)       { return c.get("tenant_id", Long.class); }
    public String getTenantSchema(Claims c) { return c.get("tenant_schema", String.class); }
    public String getType(Claims c)         { return c.get("type", String.class); }
}
