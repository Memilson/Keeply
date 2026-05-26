package com.keeply.backend.service;

import com.keeply.backend.dto.AuthDtos;
import com.keeply.backend.exception.UnauthorizedException;
import com.keeply.backend.model.Device;
import com.keeply.backend.model.UserAccount;
import com.keeply.backend.repository.DeviceRepository;
import com.keeply.backend.repository.UserRepository;
import com.keeply.backend.security.JwtService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;

@Service
public class AuthService {
    private final UserRepository users;
    private final DeviceRepository devices;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(UserRepository users, DeviceRepository devices, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.users = users;
        this.devices = devices;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @Transactional
    public AuthDtos.AuthResponse register(AuthDtos.RegisterRequest request) {
        String email = normalizedEmail(request.email());
        if (users.existsByEmail(email)) {
            throw new IllegalArgumentException("Email já cadastrado");
        }

        UserAccount user = new UserAccount();
        user.name = nullableTrim(request.name());
        user.email = email;
        user.passwordHash = passwordEncoder.encode(request.password());
        users.save(user);

        String accessToken = jwtService.generateAccessToken(user.id, user.email);
        return new AuthDtos.AuthResponse(accessToken, null, user.id, user.email, null);
    }

    @Transactional(readOnly = true)
    public AuthDtos.AuthResponse login(AuthDtos.LoginRequest request) {
        UserAccount user = authenticateUser(request.email(), request.password());
        String accessToken = jwtService.generateAccessToken(user.id, user.email);
        return new AuthDtos.AuthResponse(accessToken, null, user.id, user.email, null);
    }

    @Transactional
    public AuthDtos.AuthResponse loginDevice(AuthDtos.DeviceLoginRequest request) {
        if (isBlank(request.deviceInstallationId())) {
            throw new IllegalArgumentException("deviceInstallationId é obrigatório");
        }
        if (isBlank(request.hostname())) {
            throw new IllegalArgumentException("hostname é obrigatório");
        }

        UserAccount user = authenticateUser(request.email(), request.password());
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

        String accessToken = jwtService.generateDeviceAccessToken(user.id, user.email, device.id);
        return new AuthDtos.AuthResponse(accessToken, refreshToken, user.id, user.email, device.id);
    }

    @Transactional
    public AuthDtos.AuthResponse refresh(AuthDtos.RefreshRequest request) {
        if (isBlank(request.refreshToken()) || isBlank(request.deviceInstallationId())) {
            throw new UnauthorizedException("Refresh token inválido");
        }

        JwtService.RefreshPrincipal principal;
        try {
            principal = jwtService.parseRefreshToken(request.refreshToken().trim());
        } catch (Exception ex) {
            throw new UnauthorizedException("Refresh token inválido");
        }

        String installationId = request.deviceInstallationId().trim();
        if (!installationId.equals(principal.deviceInstallationId())) {
            throw new UnauthorizedException("Refresh token inválido");
        }

        Device device = devices.findByUserIdAndDeviceInstallationId(principal.userId(), installationId)
                .orElseThrow(() -> new UnauthorizedException("Refresh token inválido"));

        if (isBlank(device.refreshTokenHash) || !passwordEncoder.matches(normalizeRefreshToken(request.refreshToken()), device.refreshTokenHash)) {
            throw new UnauthorizedException("Refresh token revogado");
        }

        String newRefreshToken = jwtService.generateRefreshToken(principal.userId(), principal.email(), installationId);
        device.refreshTokenHash = passwordEncoder.encode(normalizeRefreshToken(newRefreshToken));
        device.lastSeenAt = Instant.now();
        devices.save(device);

        String accessToken = jwtService.generateDeviceAccessToken(principal.userId(), principal.email(), device.id);
        return new AuthDtos.AuthResponse(accessToken, newRefreshToken, principal.userId(), principal.email(), device.id);
    }

    private UserAccount authenticateUser(String email, String password) {
        String normalizedEmail = normalizedEmail(email);
        UserAccount user = users.findByEmail(normalizedEmail)
                .orElseThrow(() -> new IllegalArgumentException("Credenciais inválidas"));
        if (!passwordEncoder.matches(password, user.passwordHash)) {
            throw new IllegalArgumentException("Credenciais inválidas");
        }
        return user;
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
