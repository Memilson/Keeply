package com.keeply.backend.security;

import java.util.UUID;

public record JwtPrincipal(UUID userId, String email) {}
