package com.schoolhub.tenantservice.filter;

import com.schoolhub.tenantservice.util.JwtUtil;
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
import java.util.List;

/**
 * Downstream stateless JWT check: signature + expiry + access-type, then sets a
 * ROLE_<NAME> authority. No blacklist lookup here - revocation is an AuthService
 * concern and access tokens are short-lived.
 * ponytail: stateless downstream; add a shared blacklist check if instant revoke is needed.
 */
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;

    public JwtAuthFilter(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        // A bad/expired token must not block the request — public endpoints (signup, activity ping)
        // still need to work for a client holding a stale token. Only set auth when the token is
        // valid; otherwise proceed unauthenticated and let the authorization rules reject.
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            try {
                Claims claims = jwtUtil.parseToken(authHeader.substring(7));
                if (JwtUtil.TYPE_ACCESS.equals(jwtUtil.getType(claims))) {
                    String role = jwtUtil.getRole(claims);
                    List<SimpleGrantedAuthority> authorities = role == null
                            ? List.of()
                            : List.of(new SimpleGrantedAuthority("ROLE_" + role));
                    SecurityContextHolder.getContext().setAuthentication(
                            new UsernamePasswordAuthenticationToken(jwtUtil.getUserId(claims), null, authorities));
                }
            } catch (JwtException ignored) {
                // Invalid/expired token: proceed unauthenticated.
            }
        }
        chain.doFilter(request, response);
    }
}
