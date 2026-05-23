package com.keeply.backend.controller;

import com.keeply.backend.dto.AuthDtos;
import com.keeply.backend.service.AuthService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService auth;

    public AuthController(AuthService auth) {
        this.auth = auth;
    }

    @PostMapping("/register")
    public AuthDtos.AuthResponse register(@RequestBody AuthDtos.RegisterRequest request) {
        return auth.register(request);
    }

    @PostMapping("/login")
    public AuthDtos.AuthResponse login(@RequestBody AuthDtos.LoginRequest request) {
        return auth.login(request);
    }
}
