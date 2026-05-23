package com.keeply.backend.controller;

import com.keeply.backend.dto.AuthDtos;
import com.keeply.backend.service.AuthService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private static final Logger log = LoggerFactory.getLogger(AuthController.class);
    private final AuthService auth;

    public AuthController(AuthService auth) {
        this.auth = auth;
    }

    @PostMapping("/register")
    public AuthDtos.AuthResponse register(@RequestBody AuthDtos.RegisterRequest request) {
        log.info("Request de registro: email={}, name={}", request.email(), request.name());
        return auth.register(request);
    }

    @PostMapping("/login")
    public AuthDtos.AuthResponse login(@RequestBody AuthDtos.LoginRequest request) {
        log.info("Request de login: email='{}', password_length={}", request.email(), 
                request.password() != null ? request.password().length() : "null");
        return auth.login(request);
    }
}
