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

        // A bad/expired/revoked token must NOT block the request here — otherwise a stale token
        // sitting in a client would break public endpoints like /login. We only *set* an
        // authentication when the token is fully valid; anything else is left unauthenticated and
        // the authorization rules (anyRequest().authenticated()) reject protected paths as usual.
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            try {
                Claims claims = jwtUtil.parseToken(authHeader.substring(7));
                String jti = jwtUtil.getJti(claims);
                boolean usable = JwtUtil.TYPE_ACCESS.equals(jwtUtil.getType(claims))
                        && (jti == null || !blacklistRepo.existsByTokenJti(jti));
                if (usable) {
                    Long userId = jwtUtil.getUserId(claims);
                    String role = jwtUtil.getRole(claims);
                    List<SimpleGrantedAuthority> authorities = role == null
                            ? List.of()
                            : List.of(new SimpleGrantedAuthority("ROLE_" + role));
                    SecurityContextHolder.getContext().setAuthentication(
                            new UsernamePasswordAuthenticationToken(userId, null, authorities));
                }
            } catch (JwtException ignored) {
                // Invalid/expired token: proceed unauthenticated.
            }
        }

        chain.doFilter(request, response);
    }
}
