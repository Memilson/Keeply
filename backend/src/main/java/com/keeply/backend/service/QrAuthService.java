package com.keeply.backend.service;

import com.keeply.backend.dto.AuthDtos;
import com.keeply.backend.dto.QrAuthDtos;
import com.keeply.backend.exception.UnauthorizedException;
import com.keeply.backend.model.UserAccount;
import com.keeply.backend.repository.UserRepository;
import com.keeply.backend.security.JwtService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class QrAuthService {

    private final UserRepository userRepository;
    private final JwtService jwtService;
    
    // In-memory store for temporary QR tokens. Key: token (UUID string)
    private final Map<String, QrToken> tokenStore = new ConcurrentHashMap<>();

    private record QrToken(String token, UUID userId, String email, Instant expiresAt) {}

    public QrAuthService(UserRepository userRepository, JwtService jwtService) {
        this.userRepository = userRepository;
        this.jwtService = jwtService;
    }

    public QrAuthDtos.QrTokenResponse generateQrToken(UUID userId, String email) {
        String token = UUID.randomUUID().toString();
        Instant expiresAt = Instant.now().plus(5, ChronoUnit.MINUTES);
        
        tokenStore.put(token, new QrToken(token, userId, email, expiresAt));
        
        return new QrAuthDtos.QrTokenResponse(token, expiresAt);
    }

    public AuthDtos.AuthResponse exchangeQrToken(String tokenStr) {
        QrToken qrToken = tokenStore.remove(tokenStr); // Use and remove (one-time use)
        
        if (qrToken == null || qrToken.expiresAt().isBefore(Instant.now())) {
            throw new UnauthorizedException("Token QR inválido ou expirado");
        }

        // Verify user still exists
        UserAccount user = userRepository.findById(qrToken.userId())
                .orElseThrow(() -> new UnauthorizedException("Usuário não encontrado"));

        String accessToken = jwtService.generateAccessToken(user.id, user.email);
        
        return new AuthDtos.AuthResponse(accessToken, null, user.id, user.email, null);
    }
    
    // Clean up expired tokens periodically
    @Scheduled(fixedRate = 60000)
    public void cleanupExpiredTokens() {
        Instant now = Instant.now();
        tokenStore.entrySet().removeIf(entry -> entry.getValue().expiresAt().isBefore(now));
    }
}
