package com.keeply.backend.service;

import com.keeply.backend.dto.AuthDtos;
import com.keeply.backend.model.UserAccount;
import com.keeply.backend.repository.UserRepository;
import com.keeply.backend.security.JwtService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {
    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(UserRepository users, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @Transactional
    public AuthDtos.AuthResponse register(AuthDtos.RegisterRequest request) {
        if (users.existsByEmail(request.email())) {
            throw new IllegalArgumentException("Email já cadastrado");
        }

        UserAccount user = new UserAccount();
        user.name = request.name();
        user.email = request.email().toLowerCase().trim();
        user.passwordHash = passwordEncoder.encode(request.password());
        users.save(user);

        String token = jwtService.generate(user.id, user.email);
        return new AuthDtos.AuthResponse(token, user.id, user.email);
    }

    @Transactional(readOnly = true)
    public AuthDtos.AuthResponse login(AuthDtos.LoginRequest request) {
        UserAccount user = users.findByEmail(request.email().toLowerCase().trim())
                .orElseThrow(() -> new IllegalArgumentException("Credenciais inválidas"));

        if (!passwordEncoder.matches(request.password(), user.passwordHash)) {
            throw new IllegalArgumentException("Credenciais inválidas");
        }

        String token = jwtService.generate(user.id, user.email);
        return new AuthDtos.AuthResponse(token, user.id, user.email);
    }
}
