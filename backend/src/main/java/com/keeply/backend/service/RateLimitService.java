package com.keeply.backend.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * VULN-005: Rate Limit com Caffeine (in-memory).
 *
 * AVISO: Em produção com múltiplas réplicas, os contadores são independentes por instância.
 * Para rate limiting distribuído, use Redis + spring-boot-starter-data-redis
 * ou configure o rate limit no API Gateway (Nginx/Traefik/Kong).
 */
@Service
public class RateLimitService {
    private static final Logger log = LoggerFactory.getLogger(RateLimitService.class);
    private final int maxAttemptsIp;
    private final int maxAttemptsEmail;
    private final int maxRefreshAttemptsIp;
    private final int loginIpWindowMinutes;
    private final int loginEmailWindowMinutes;
    private final int maxFileDownloadAttemptsUser;
    private final int maxArchiveDownloadAttemptsUser;
    private final int fileDownloadWindowMinutes;
    private final int archiveDownloadWindowMinutes;
    private final Cache<String, AtomicInteger> ipCache;
    private final Cache<String, AtomicInteger> emailCache;
    private final Cache<String, AtomicInteger> refreshIpCache;
    private final Cache<String, AtomicInteger> fileDownloadUserCache;
    private final Cache<String, AtomicInteger> archiveDownloadUserCache;

    public RateLimitService(
            @Value("${keeply.security.rate-limit.login.ip.max-attempts:5}") int maxAttemptsIp,
            @Value("${keeply.security.rate-limit.login.ip.window-minutes:15}") int loginIpWindowMinutes,
            @Value("${keeply.security.rate-limit.login.email.max-attempts:5}") int maxAttemptsEmail,
            @Value("${keeply.security.rate-limit.login.email.window-minutes:15}") int loginEmailWindowMinutes,
            @Value("${keeply.security.rate-limit.refresh.ip.max-attempts:30}") int maxRefreshAttemptsIp,
            @Value("${keeply.security.rate-limit.download.file.user.max-attempts:10}") int maxFileDownloadAttemptsUser,
            @Value("${keeply.security.rate-limit.download.file.user.window-minutes:5}") int fileDownloadWindowMinutes,
            @Value("${keeply.security.rate-limit.download.archive.user.max-attempts:1}") int maxArchiveDownloadAttemptsUser,
            @Value("${keeply.security.rate-limit.download.archive.user.window-minutes:10}") int archiveDownloadWindowMinutes) {
        this.maxAttemptsIp = maxAttemptsIp;
        this.maxAttemptsEmail = maxAttemptsEmail;
        this.maxRefreshAttemptsIp = maxRefreshAttemptsIp;
        this.loginIpWindowMinutes = loginIpWindowMinutes;
        this.loginEmailWindowMinutes = loginEmailWindowMinutes;
        this.maxFileDownloadAttemptsUser = maxFileDownloadAttemptsUser;
        this.maxArchiveDownloadAttemptsUser = maxArchiveDownloadAttemptsUser;
        this.fileDownloadWindowMinutes = fileDownloadWindowMinutes;
        this.archiveDownloadWindowMinutes = archiveDownloadWindowMinutes;
        this.ipCache = Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofMinutes(loginIpWindowMinutes))
                .build();
        
        this.emailCache = Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofMinutes(loginEmailWindowMinutes))
                .build();
        this.refreshIpCache = Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofMinutes(1))
                .build();
        this.fileDownloadUserCache = Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofMinutes(fileDownloadWindowMinutes))
                .build();
        this.archiveDownloadUserCache = Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofMinutes(archiveDownloadWindowMinutes))
                .build();

        // VULN-005: aviso de startup — lembrar ops team sobre a limitação em produção
        log.warn("[SECURITY] RateLimitService usando cache IN-MEMORY. "
                + "Em produção multi-instância configure Redis ou rate limit no API Gateway.");
    }

    public void checkRateLimit(String ip, String email) {
        ip = normalizeKey(ip);
        email = normalizeEmail(email);
        if (ip != null) {
            int attempts = getAttempts(ipCache, ip);
            if (attempts >= maxAttemptsIp) {
                throw new RateLimitException("Muitas tentativas de login a partir deste IP. Tente novamente em " + loginIpWindowMinutes + " minutos.");
            }
        }

        if (email != null) {
            int attempts = getAttempts(emailCache, email);
            if (attempts >= maxAttemptsEmail) {
                throw new RateLimitException("Conta temporariamente bloqueada por excesso de tentativas falhas. Tente novamente em " + loginEmailWindowMinutes + " minutos.");
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

    public void checkAndRecordFileDownloadAttempt(String userKey) {
        checkAndRecordAttempt(
                fileDownloadUserCache,
                normalizeKey(userKey),
                maxFileDownloadAttemptsUser,
                "Muitos downloads de arquivos em pouco tempo. Tente novamente em " + fileDownloadWindowMinutes + " minutos."
        );
    }

    public void checkAndRecordArchiveDownloadAttempt(String userKey) {
        checkAndRecordAttempt(
                archiveDownloadUserCache,
                normalizeKey(userKey),
                maxArchiveDownloadAttemptsUser,
                "Muitos downloads de arquivos compactados em pouco tempo. Tente novamente em " + archiveDownloadWindowMinutes + " minutos."
        );
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

    private void checkAndRecordAttempt(Cache<String, AtomicInteger> cache, String key, int maxAttempts, String message) {
        if (key == null) {
            return;
        }
        int attempts = incrementAttempts(cache, key);
        if (attempts > maxAttempts) {
            throw new RateLimitException(message);
        }
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
