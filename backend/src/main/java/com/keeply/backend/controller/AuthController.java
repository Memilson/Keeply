package com.keeply.backend.controller;

import com.keeply.backend.dto.AuthDtos;
import com.keeply.backend.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import java.net.InetAddress;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService auth;
    private final com.keeply.backend.service.QrAuthService qrAuth;
    private final boolean trustProxyHeaders;
    private final Set<String> trustedProxyIps;

    public AuthController(AuthService auth,
                          com.keeply.backend.service.QrAuthService qrAuth,
                          @Value("${keeply.security.trust-proxy-headers:false}") boolean trustProxyHeaders,
                          @Value("${keeply.security.trusted-proxy-ips:}") String trustedProxyIps) {
        this.auth = auth;
        this.qrAuth = qrAuth;
        this.trustProxyHeaders = trustProxyHeaders;
        this.trustedProxyIps = Set.of(trustedProxyIps.split(",")).stream()
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .collect(Collectors.toUnmodifiableSet());
    }

    @PostMapping("/register")
    public AuthDtos.AuthResponse register(@Valid @RequestBody AuthDtos.RegisterRequest request, HttpServletRequest servletRequest) {
        return auth.register(request, getClientIp(servletRequest));
    }

    @PostMapping("/login")
    public AuthDtos.AuthResponse login(@Valid @RequestBody AuthDtos.LoginRequest request, HttpServletRequest servletRequest) {
        return auth.login(request, getClientIp(servletRequest));
    }

    @PostMapping("/login-device")
    public AuthDtos.AuthResponse loginDevice(@Valid @RequestBody AuthDtos.DeviceLoginRequest request, HttpServletRequest servletRequest) {
        return auth.loginDevice(request, getClientIp(servletRequest));
    }

    @PostMapping("/refresh")
    public AuthDtos.AuthResponse refresh(@Valid @RequestBody AuthDtos.RefreshRequest request, HttpServletRequest servletRequest) {
        return auth.refresh(request, getClientIp(servletRequest));
    }

    @GetMapping("/qr")
    public com.keeply.backend.dto.QrAuthDtos.QrTokenResponse generateQrToken() {
        var principal = com.keeply.backend.util.CurrentUser.get();
        return qrAuth.generateQrToken(principal.userId(), principal.email());
    }

    @PostMapping("/qr/exchange")
    public AuthDtos.AuthResponse exchangeQrToken(@Valid @RequestBody com.keeply.backend.dto.QrAuthDtos.QrExchangeRequest request) {
        return qrAuth.exchangeQrToken(request.token());
    }

    private String getClientIp(HttpServletRequest request) {
        String remoteAddr = request.getRemoteAddr();
        if (trustProxyHeaders && trustedProxyIps.contains(remoteAddr)) {
            String xf = request.getHeader("X-Forwarded-For");
            String forwardedIp = firstValidForwardedIp(xf);
            if (forwardedIp != null) {
                return forwardedIp;
            }
        }
        return remoteAddr;
    }

    private String firstValidForwardedIp(String forwardedFor) {
        if (forwardedFor == null || forwardedFor.isBlank() || forwardedFor.length() > 512) {
            return null;
        }
        for (String raw : forwardedFor.split(",")) {
            String candidate = raw.trim();
            if (candidate.isEmpty()) {
                continue;
            }
            try {
                return InetAddress.getByName(candidate).getHostAddress();
            } catch (Exception ignored) {
            }
        }
        return null;
    }
}
