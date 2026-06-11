package com.keeply.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.keeply.backend.dto.AuthDtos;
import com.keeply.backend.exception.UnauthorizedException;
import com.keeply.backend.model.AuditLog;
import com.keeply.backend.model.Device;
import com.keeply.backend.model.UserAccount;
import com.keeply.backend.repository.AuditLogRepository;
import com.keeply.backend.repository.DeviceRepository;
import com.keeply.backend.repository.UserRepository;
import com.keeply.backend.security.JwtService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

@Service
public class AuthService {
    private final UserRepository users;
    private final DeviceRepository devices;
    private final AuditLogRepository auditLogs;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RateLimitService rateLimit;
    private final ObjectMapper objectMapper;
    private final String registrationCode;

    public AuthService(UserRepository users, DeviceRepository devices, AuditLogRepository auditLogs,
                       PasswordEncoder passwordEncoder, JwtService jwtService, RateLimitService rateLimit,
                       ObjectMapper objectMapper,
                       @Value("${keeply.security.registration-code:}") String registrationCode) {
        this.users = users;
        this.devices = devices;
        this.auditLogs = auditLogs;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.rateLimit = rateLimit;
        this.objectMapper = objectMapper;
        this.registrationCode = registrationCode == null ? "" : registrationCode.trim();
    }

    @Transactional
    public AuthDtos.AuthResponse register(AuthDtos.RegisterRequest request, String ip) {
        rateLimit.checkRateLimit(ip, request.email());
        validateRegistrationCode(request.registrationCode());
        String email = normalizedEmail(request.email());
        if (users.existsByEmail(email)) {
            recordAudit(null, null, "REGISTER_FAILED", "Tentativa de registro com email ja existente: " + email, ip);
            rateLimit.recordFailure(ip, email);
            throw new IllegalArgumentException("Email ja cadastrado");
        }

        UserAccount user = new UserAccount();
        // VULN-019: @NotBlank no DTO garante que name não é branco;
        // usar trim() direto evita null retornado por nullableTrim() que quebraria o NOT NULL do banco
        user.name = request.name().trim();
        user.email = email;
        user.passwordHash = passwordEncoder.encode(request.password());
        users.save(user);

        rateLimit.recordSuccess(email);
        recordAudit(user, null, "REGISTER_SUCCESS", "Usuario registrado com sucesso", ip);
        String accessToken = jwtService.generateAccessToken(user.id, user.email);
        return new AuthDtos.AuthResponse(accessToken, null, user.id, user.email, null);
    }

    private void validateRegistrationCode(String providedCode) {
        if (isBlank(registrationCode)) {
            throw new IllegalStateException("Registro desabilitado");
        }
        String provided = providedCode == null ? "" : providedCode.trim();
        boolean valid = MessageDigest.isEqual(
                registrationCode.getBytes(StandardCharsets.UTF_8),
                provided.getBytes(StandardCharsets.UTF_8)
        );
        if (!valid) {
            throw new IllegalArgumentException("Codigo de registro invalido");
        }
    }

    @Transactional
    public AuthDtos.AuthResponse login(AuthDtos.LoginRequest request, String ip) {
        rateLimit.checkRateLimit(ip, request.email());
        try {
            UserAccount user = authenticateUser(request.email(), request.password(), ip);
            rateLimit.recordSuccess(request.email());
            recordAudit(user, null, "LOGIN_SUCCESS", "Login realizado via Web/API", ip);
            String accessToken = jwtService.generateAccessToken(user.id, user.email);
            return new AuthDtos.AuthResponse(accessToken, null, user.id, user.email, null);
        } catch (Exception e) {
            rateLimit.recordFailure(ip, request.email());
            throw e;
        }
    }

    @Transactional
    public AuthDtos.AuthResponse loginDevice(AuthDtos.DeviceLoginRequest request, String ip) {
        rateLimit.checkRateLimit(ip, request.email());
        if (isBlank(request.deviceInstallationId())) {
            throw new IllegalArgumentException("deviceInstallationId e obrigatorio");
        }
        if (isBlank(request.hostname())) {
            throw new IllegalArgumentException("hostname e obrigatorio");
        }

        UserAccount user;
        try {
            user = authenticateUser(request.email(), request.password(), ip);
            rateLimit.recordSuccess(request.email());
        } catch (Exception e) {
            rateLimit.recordFailure(ip, request.email());
            throw e;
        }

        String installationId = request.deviceInstallationId().trim();

        Device device = devices.findByUserIdAndDeviceInstallationId(user.id, installationId)
                .orElseGet(Device::new);

        device.user = user;
        device.deviceInstallationId = installationId;
        device.hostname = request.hostname().trim();
        device.name = request.hostname().trim();
        device.osName = nullableTrim(request.osName());
        device.agentVersion = nullableTrim(request.agentVersion());
        device.lastSeenAt = Instant.now();

        String refreshToken = jwtService.generateRefreshToken(user.id, user.email, installationId);
        device.refreshTokenHash = passwordEncoder.encode(normalizeRefreshToken(refreshToken));
        devices.save(device);

        recordAudit(user, device, "DEVICE_LOGIN_SUCCESS", "Login de dispositivo realizado: " + device.hostname, ip);

        String accessToken = jwtService.generateDeviceAccessToken(user.id, user.email, device.id);
        return new AuthDtos.AuthResponse(accessToken, refreshToken, user.id, user.email, device.id);
    }

    @Transactional
    public AuthDtos.AuthResponse refresh(AuthDtos.RefreshRequest request, String ip) {
        rateLimit.checkAndRecordRefreshAttempt(ip);

        if (isBlank(request.refreshToken()) || isBlank(request.deviceInstallationId())) {
            throw new UnauthorizedException("Refresh token invalido");
        }

        JwtService.RefreshPrincipal principal;
        try {
            principal = jwtService.parseRefreshToken(request.refreshToken().trim());
        } catch (Exception ex) {
            throw new UnauthorizedException("Refresh token invalido");
        }

        String installationId = request.deviceInstallationId().trim();
        if (!installationId.equals(principal.deviceInstallationId())) {
            throw new UnauthorizedException("Refresh token invalido");
        }

        Device device = devices.findByUserIdAndDeviceInstallationId(principal.userId(), installationId)
                .orElseThrow(() -> new UnauthorizedException("Refresh token invalido"));

        if (isBlank(device.refreshTokenHash) || !passwordEncoder.matches(normalizeRefreshToken(request.refreshToken()), device.refreshTokenHash)) {
            recordAudit(device.user, device, "REFRESH_FAILED", "Tentativa de refresh com token revogado ou invalido", ip);
            throw new UnauthorizedException("Refresh token revogado");
        }

        String newRefreshToken = jwtService.generateRefreshToken(principal.userId(), principal.email(), installationId);
        device.refreshTokenHash = passwordEncoder.encode(normalizeRefreshToken(newRefreshToken));
        device.lastSeenAt = Instant.now();
        devices.save(device);

        String accessToken = jwtService.generateDeviceAccessToken(principal.userId(), principal.email(), device.id);
        return new AuthDtos.AuthResponse(accessToken, newRefreshToken, principal.userId(), principal.email(), device.id);
    }

    private UserAccount authenticateUser(String email, String password, String ip) {
        String normalizedEmail = normalizedEmail(email);
        Optional<UserAccount> userOpt = users.findByEmail(normalizedEmail);

        if (userOpt.isEmpty()) {
            // VULN-008: mensagem genérica — não revelar se o email existe ou não
            recordAudit(null, null, "LOGIN_FAILED", "Credenciais invalidas", ip);
            throw new IllegalArgumentException("Credenciais invalidas");
        }

        UserAccount user = userOpt.get();
        if (!passwordEncoder.matches(password, user.passwordHash)) {
            // VULN-008: mensagem genérica — não revelar que o email existe mas a senha é errada
            recordAudit(user, null, "LOGIN_FAILED", "Credenciais invalidas", ip);
            throw new IllegalArgumentException("Credenciais invalidas");
        }
        return user;
    }

    private void recordAudit(UserAccount user, Device device, String type, String message, String ip) {
        AuditLog log = new AuditLog();
        log.user = user;
        log.device = device;
        log.eventType = type;
        log.message = message;
        log.metadataJson = serializeAuditMetadata(ip);
        auditLogs.save(log);
    }

    private String serializeAuditMetadata(String ip) {
        try {
            return objectMapper.writeValueAsString(Map.of("ip", ip == null ? "" : ip));
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao serializar metadata de auditoria", e);
        }
    }

    private String normalizedEmail(String email) {
        if (isBlank(email)) {
            throw new IllegalArgumentException("email é obrigatório");
        }
        return email.toLowerCase().trim();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String nullableTrim(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isBlank() ? null : trimmed;
    }

    private String normalizeRefreshToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(hashed.length * 2);
            for (byte b : hashed) {
                hex.append(Character.forDigit((b >> 4) & 0xF, 16));
                hex.append(Character.forDigit(b & 0xF, 16));
            }
            return hex.toString();
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao processar refresh token", e);
        }
    }
}
