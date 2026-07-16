package com.schoolhub.schoolservice.filter;

import com.schoolhub.schoolservice.tenant.TenantContext;
import com.schoolhub.schoolservice.util.JwtUtil;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Validates the access token AND binds the request to the caller's school schema
 * (from the tenant_schema claim) so every DB query this request makes is isolated
 * to that school. The tenant binding is always cleared when the request ends.
 */
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;

    public JwtAuthFilter(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        // A bad/expired token must not block the request — public endpoints (e.g. activity ping)
        // still need to work for a client holding a stale token. Only set auth + tenant binding
        // when the token is valid; otherwise proceed unauthenticated.
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            try {
                Claims claims = jwtUtil.parseToken(authHeader.substring(7));
                if (JwtUtil.TYPE_ACCESS.equals(jwtUtil.getType(claims))) {
                    String role = jwtUtil.getRole(claims);
                    List<SimpleGrantedAuthority> authorities = new ArrayList<>();
                    if (role != null) {
                        authorities.add(new SimpleGrantedAuthority("ROLE_" + role));
                        // A principal is senior school staff with admin-equivalent access.
                        if ("PRINCIPAL".equals(role)) authorities.add(new SimpleGrantedAuthority("ROLE_ADMIN"));
                    }
                    SecurityContextHolder.getContext().setAuthentication(
                            new UsernamePasswordAuthenticationToken(jwtUtil.getUserId(claims), null, authorities));

                    TenantContext.setUserId(jwtUtil.getUserId(claims));
                    TenantContext.setTenantId(jwtUtil.getTenantId(claims));
                    String tenantSchema = jwtUtil.getTenantSchema(claims);
                    if (tenantSchema != null) {
                        TenantContext.set(tenantSchema);
                    }
                }
            } catch (JwtException ignored) {
                // Invalid/expired token: proceed unauthenticated.
            }
        }

        try {
            chain.doFilter(request, response);
        } finally {
            TenantContext.clear();
        }
    }
}
