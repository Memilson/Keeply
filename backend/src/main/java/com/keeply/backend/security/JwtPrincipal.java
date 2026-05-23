/*
 * Representa a identidade de um usuário autenticado no sistema.
 * É extraído de um token JWT validado e utilizado como objeto principal no contexto
 * de autenticação do Spring Security, contendo o ID único e o email do usuário.
 */
package com.keeply.backend.security;

import java.util.UUID;

public record JwtPrincipal(UUID userId, String email) {}
