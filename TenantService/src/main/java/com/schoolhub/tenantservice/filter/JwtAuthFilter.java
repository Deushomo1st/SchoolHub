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
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            chain.doFilter(request, response);
            return;
        }
        try {
            Claims claims = jwtUtil.parseToken(authHeader.substring(7));
            if (!JwtUtil.TYPE_ACCESS.equals(jwtUtil.getType(claims))) {
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Refresh tokens cannot authenticate API calls");
                return;
            }
            String role = jwtUtil.getRole(claims);
            List<SimpleGrantedAuthority> authorities = role == null
                    ? List.of()
                    : List.of(new SimpleGrantedAuthority("ROLE_" + role));
            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken(jwtUtil.getUserId(claims), null, authorities));
        } catch (JwtException e) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid or expired token");
            return;
        }
        chain.doFilter(request, response);
    }
}
