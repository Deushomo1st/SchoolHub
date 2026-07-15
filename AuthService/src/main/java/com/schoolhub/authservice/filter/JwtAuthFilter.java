package com.schoolhub.authservice.filter;

import com.schoolhub.authservice.repository.TokenBlacklistRepository;
import com.schoolhub.authservice.util.JwtUtil;
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
 * Validates the Bearer access token on every request: signature + expiry (JJWT),
 * rejects refresh tokens, checks the logout blacklist, and sets a ROLE_<NAME>
 * authority so @PreAuthorize works. No Bearer header → continue (permit-list paths).
 */
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final TokenBlacklistRepository blacklistRepo;

    public JwtAuthFilter(JwtUtil jwtUtil, TokenBlacklistRepository blacklistRepo) {
        this.jwtUtil = jwtUtil;
        this.blacklistRepo = blacklistRepo;
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
            String jti = jwtUtil.getJti(claims);
            if (jti != null && blacklistRepo.existsByTokenJti(jti)) {
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Token has been revoked");
                return;
            }
            Long userId = jwtUtil.getUserId(claims);
            String role = jwtUtil.getRole(claims);
            List<SimpleGrantedAuthority> authorities = role == null
                    ? List.of()
                    : List.of(new SimpleGrantedAuthority("ROLE_" + role));
            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken(userId, null, authorities));
        } catch (JwtException e) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid or expired token");
            return;
        }

        chain.doFilter(request, response);
    }
}
