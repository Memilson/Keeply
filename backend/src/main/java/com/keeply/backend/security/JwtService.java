package com.keeply.backend.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

@Service
public class JwtService {
    private static final String CLAIM_EMAIL = "email";
    private static final String CLAIM_TYPE = "typ";
    private static final String CLAIM_INSTALLATION_ID = "installationId";
    private static final String CLAIM_DEVICE_ID = "deviceId";

    private final SecretKey key;
    private final long accessExpirationMinutes;
    private final long refreshExpirationDays;

    public JwtService(
            @Value("${keeply.jwt.secret}") String secret,
            @Value("${keeply.jwt.expiration-minutes}") long accessExpirationMinutes,
            @Value("${keeply.jwt.refresh-expiration-days:30}") long refreshExpirationDays
    ) {
        if (secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalArgumentException("keeply.jwt.secret precisa ter pelo menos 32 bytes");
        }
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessExpirationMinutes = accessExpirationMinutes;
        this.refreshExpirationDays = refreshExpirationDays;
    }

    public String generateAccessToken(UUID userId, String email) {
        return generateToken(userId, email, "access", null, null,
                Instant.now().plusSeconds(accessExpirationMinutes * 60));
    }

    public String generateDeviceAccessToken(UUID userId, String email, UUID deviceId) {
        return generateToken(userId, email, "access", null, deviceId,
                Instant.now().plusSeconds(accessExpirationMinutes * 60));
    }

    public String generateRefreshToken(UUID userId, String email, String installationId) {
        return generateToken(userId, email, "refresh", installationId, null,
                Instant.now().plusSeconds(refreshExpirationDays * 24 * 60 * 60));
    }

    public JwtPrincipal parseAccessToken(String token) {
        Claims claims = parseClaims(token);
        if (!"access".equals(claims.get(CLAIM_TYPE, String.class))) {
            throw new IllegalArgumentException("Tipo de token inválido");
        }
        return toPrincipal(claims);
    }

    public RefreshPrincipal parseRefreshToken(String token) {
        Claims claims = parseClaims(token);
        if (!"refresh".equals(claims.get(CLAIM_TYPE, String.class))) {
            throw new IllegalArgumentException("Tipo de token inválido");
        }
        JwtPrincipal principal = toPrincipal(claims);
        String installationId = claims.get(CLAIM_INSTALLATION_ID, String.class);
        return new RefreshPrincipal(principal.userId(), principal.email(), installationId);
    }

    private String generateToken(UUID userId, String email, String type, String installationId, UUID deviceId, Instant expiresAt) {
        Instant now = Instant.now();
        var builder = Jwts.builder()
                .subject(userId.toString())
                .claim(CLAIM_EMAIL, email)
                .claim(CLAIM_TYPE, type)
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt));
        if (installationId != null) {
            builder.claim(CLAIM_INSTALLATION_ID, installationId);
        }
        if (deviceId != null) {
            builder.claim(CLAIM_DEVICE_ID, deviceId.toString());
        }
        return builder.signWith(key).compact();
    }

    private Claims parseClaims(String token) {
        return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
    }

    private JwtPrincipal toPrincipal(Claims claims) {
        UUID userId = UUID.fromString(claims.getSubject());
        String email = claims.get(CLAIM_EMAIL, String.class);
        String deviceId = claims.get(CLAIM_DEVICE_ID, String.class);
        return new JwtPrincipal(userId, email, deviceId == null ? null : UUID.fromString(deviceId));
    }

    public record RefreshPrincipal(UUID userId, String email, String deviceInstallationId) {
    }
}
