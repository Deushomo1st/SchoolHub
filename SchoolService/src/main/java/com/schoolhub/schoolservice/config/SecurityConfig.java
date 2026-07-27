package com.schoolhub.schoolservice.config;

import com.schoolhub.schoolservice.filter.JwtAuthFilter;
import com.schoolhub.schoolservice.util.JwtUtil;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtUtil jwtUtil;

    public SecurityConfig(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    @Bean
    public BCryptPasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .httpBasic(b -> b.disable())
                .formLogin(f -> f.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/health", "/actuator/**").permitAll()
                        // Internal endpoints are for same-host services only (the API gateway),
                        // never the public internet — accept IPv4 + IPv6 loopback.
                        .requestMatchers("/internal/**").access((authentication, context) -> {
                            String addr = context.getRequest().getRemoteAddr();
                            boolean loopback = "127.0.0.1".equals(addr)
                                    || "0:0:0:0:0:0:0:1".equals(addr)
                                    || "::1".equals(addr);
                            return new AuthorizationDecision(loopback);
                        })
                        // Stripe webhooks carry no JWT — authenticity is the signature check.
                        .requestMatchers("/api/v1/stripe/webhook").permitAll()
                        .anyRequest().authenticated()
                )
                .addFilterBefore(new JwtAuthFilter(jwtUtil), UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}