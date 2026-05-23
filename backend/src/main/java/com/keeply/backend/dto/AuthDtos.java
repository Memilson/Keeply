package com.keeply.backend.dto;

import java.util.UUID;

public final class AuthDtos {
    private AuthDtos() {}

    public record RegisterRequest(String name, String email, String password) {}
    public record LoginRequest(String email, String password) {}
    public record AuthResponse(String accessToken, UUID userId, String email) {}
}
