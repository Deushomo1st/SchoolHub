package com.schoolhub.authservice.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory brute-force throttle on login. After maxAttempts failures within the
 * window, the email is locked for lockMinutes; a successful login (or admin reset)
 * clears it.
 * ponytail: per-instance map - swap for Redis / bucket4j if AuthService is scaled out.
 */
@Component
public class LoginRateLimiter {

    private final int maxAttempts;
    private final Duration window;
    private final Duration lock;
    private final ConcurrentHashMap<String, Attempt> attempts = new ConcurrentHashMap<>();

    public LoginRateLimiter(
            @Value("${security.login.max-attempts:5}") int maxAttempts,
            @Value("${security.login.window-minutes:15}") int windowMinutes,
            @Value("${security.login.lock-minutes:15}") int lockMinutes) {
        this.maxAttempts = maxAttempts;
        this.window = Duration.ofMinutes(windowMinutes);
        this.lock = Duration.ofMinutes(lockMinutes);
    }

    /** @return seconds remaining on a lock, or 0 if not locked. */
    public long lockedSeconds(String email) {
        Attempt a = attempts.get(key(email));
        if (a == null || a.lockedUntil == null) return 0;
        long secs = Duration.between(Instant.now(), a.lockedUntil).getSeconds();
        return Math.max(0, secs);
    }

    public void recordFailure(String email) {
        attempts.compute(key(email), (k, a) -> {
            Instant now = Instant.now();
            if (a == null) a = new Attempt(now);
            if (a.lockedUntil != null && now.isBefore(a.lockedUntil)) return a;     // already locked
            if (now.isAfter(a.windowStart.plus(window))) a = new Attempt(now);      // window expired - reset
            a.count++;
            if (a.count >= maxAttempts) a.lockedUntil = now.plus(lock);
            return a;
        });
    }

    public void recordSuccess(String email) {
        attempts.remove(key(email));
    }

    private static String key(String email) {
        return email == null ? "" : email.trim().toLowerCase();
    }

    private static final class Attempt {
        final Instant windowStart;
        int count;
        Instant lockedUntil;
        Attempt(Instant start) { this.windowStart = start; }
    }
}
