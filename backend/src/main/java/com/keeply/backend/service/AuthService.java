package com.keeply.backend.service;

import com.keeply.backend.dto.AuthDtos;
import com.keeply.backend.model.UserAccount;
import com.keeply.backend.repository.UserRepository;
import com.keeply.backend.security.JwtService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {
    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
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
        String email = request.email().toLowerCase().trim();
        log.info("Tentativa de login para: '{}'", email);
        
        UserAccount user = users.findByEmail(email)
                .orElseThrow(() -> {
                    log.warn("Usuário não encontrado: '{}'", email);
                    return new IllegalArgumentException("Credenciais inválidas");
                });

        boolean matches = passwordEncoder.matches(request.password(), user.passwordHash);
        log.info("Password matches for '{}': {}", email, matches);

        if (!matches) {
            throw new IllegalArgumentException("Credenciais inválidas");
        }

        String token = jwtService.generate(user.id, user.email);
        return new AuthDtos.AuthResponse(token, user.id, user.email);
    }
}
