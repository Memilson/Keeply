package com.keeply.backend.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class RateLimitService {
    private final int maxAttemptsIp;
    private final int maxAttemptsEmail;
    private final int maxRefreshAttemptsIp;
    private final Cache<String, AtomicInteger> ipCache;
    private final Cache<String, AtomicInteger> emailCache;
    private final Cache<String, AtomicInteger> refreshIpCache;

    public RateLimitService(
            @Value("${keeply.security.rate-limit.login.ip.max-attempts:20}") int maxAttemptsIp,
            @Value("${keeply.security.rate-limit.login.email.max-attempts:5}") int maxAttemptsEmail,
            @Value("${keeply.security.rate-limit.refresh.ip.max-attempts:30}") int maxRefreshAttemptsIp) {
        this.maxAttemptsIp = maxAttemptsIp;
        this.maxAttemptsEmail = maxAttemptsEmail;
        this.maxRefreshAttemptsIp = maxRefreshAttemptsIp;
        this.ipCache = Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofMinutes(1))
                .build();
        
        this.emailCache = Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofMinutes(5))
                .build();
        this.refreshIpCache = Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofMinutes(1))
                .build();
    }

    public void checkRateLimit(String ip, String email) {
        ip = normalizeKey(ip);
        email = normalizeEmail(email);
        if (ip != null) {
            int attempts = getAttempts(ipCache, ip);
            if (attempts >= maxAttemptsIp) {
                throw new RateLimitException("Muitas tentativas de login a partir deste IP. Tente novamente em 1 minuto.");
            }
        }

        if (email != null) {
            int attempts = getAttempts(emailCache, email);
            if (attempts >= maxAttemptsEmail) {
                throw new RateLimitException("Conta temporariamente bloqueada por excesso de tentativas falhas. Tente novamente em 5 minutos.");
            }
        }
    }

    public void checkAndRecordRefreshAttempt(String ip) {
        ip = normalizeKey(ip);
        if (ip == null) {
            return;
        }
        int attempts = incrementAttempts(refreshIpCache, ip);
        if (attempts > maxRefreshAttemptsIp) {
            throw new RateLimitException("Muitas tentativas de refresh a partir deste IP. Tente novamente em 1 minuto.");
        }
    }

    public void recordFailure(String ip, String email) {
        ip = normalizeKey(ip);
        email = normalizeEmail(email);
        if (ip != null) {
            incrementAttempts(ipCache, ip);
        }
        if (email != null) {
            incrementAttempts(emailCache, email);
        }
    }

    public void recordSuccess(String email) {
        email = normalizeEmail(email);
        if (email != null) {
            emailCache.invalidate(email);
        }
    }

    private int getAttempts(Cache<String, AtomicInteger> cache, String key) {
        AtomicInteger attempts = cache.getIfPresent(key);
        return attempts != null ? attempts.get() : 0;
    }

    private int incrementAttempts(Cache<String, AtomicInteger> cache, String key) {
        AtomicInteger attempts = cache.get(key, k -> new AtomicInteger(0));
        return attempts == null ? 0 : attempts.incrementAndGet();
    }

    private String normalizeKey(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isBlank() ? null : trimmed;
    }

    private String normalizeEmail(String email) {
        String normalized = normalizeKey(email);
        return normalized == null ? null : normalized.toLowerCase();
    }

    public static class RateLimitException extends RuntimeException {
        public RateLimitException(String message) {
            super(message);
        }
    }
}
