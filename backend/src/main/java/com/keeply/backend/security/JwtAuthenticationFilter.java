package com.keeply.backend.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    private final JwtService jwtService;

    public JwtAuthenticationFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");

        if (header != null && header.startsWith("Bearer ")) {
            try {
                String token = header.substring("Bearer ".length());
                JwtPrincipal principal = jwtService.parseAccessToken(token);

                var auth = new UsernamePasswordAuthenticationToken(principal, null, List.of());
                auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(auth);
            } catch (ExpiredJwtException e) {
                // VULN-016: token expirado é esperado; não é um erro
                log.debug("JWT expirado para {} {}", request.getMethod(), request.getRequestURI());
                SecurityContextHolder.clearContext();
            } catch (JwtException e) {
                // VULN-016: token malformado ou assinatura inválida — suspeito
                log.warn("JWT inválido em {} {}: {}", request.getMethod(), request.getRequestURI(), e.getMessage());
                SecurityContextHolder.clearContext();
            } catch (Exception e) {
                // VULN-016: erro inesperado ao processar JWT
                log.error("Erro inesperado ao processar JWT em {} {}", request.getMethod(), request.getRequestURI(), e);
                SecurityContextHolder.clearContext();
            }
        }

        chain.doFilter(request, response);
    }
}
