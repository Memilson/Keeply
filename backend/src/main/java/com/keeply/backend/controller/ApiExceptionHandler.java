package com.keeply.backend.controller;

import com.keeply.backend.exception.ForbiddenException;
import com.keeply.backend.exception.NotFoundException;
import com.keeply.backend.exception.UnauthorizedException;
import com.keeply.backend.service.RateLimitService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class ApiExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(RateLimitService.RateLimitException.class)
    @ResponseStatus(HttpStatus.TOO_MANY_REQUESTS)
    public Map<String, Object> rateLimit(RateLimitService.RateLimitException ex) {
        log.warn("Rate limit atingido: {}", ex.getMessage());
        return errorResponse(ex.getMessage());
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public Map<String, Object> conflict(ObjectOptimisticLockingFailureException ex) {
        log.warn("Conflito de atualização concorrente: {}", ex.getMessage());
        return errorResponse("Operação concorrente detectada; tente novamente");
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalState(IllegalStateException ex) {
        if (ex.getMessage() != null && ex.getMessage().contains("snapshot em execução")) {
            log.warn("Snapshot já em execução: {}", ex.getMessage());
            return ResponseEntity.status(HttpStatus.CONFLICT).body(errorResponse(ex.getMessage()));
        }
        log.warn("Estado inválido: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse(ex.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, Object> badRequest(IllegalArgumentException ex) {
        log.warn("Requisição inválida: {}", ex.getMessage());
        return errorResponse(ex.getMessage());
    }

    @ExceptionHandler(UnauthorizedException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public Map<String, Object> unauthorized(UnauthorizedException ex) {
        log.warn("Acesso não autorizado: {}", ex.getMessage());
        return errorResponse(ex.getMessage());
    }

    @ExceptionHandler(ForbiddenException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public Map<String, Object> forbidden(ForbiddenException ex) {
        log.warn("Acesso proibido: {}", ex.getMessage());
        return errorResponse(ex.getMessage());
    }

    @ExceptionHandler(NotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Map<String, Object> notFound(NotFoundException ex) {
        log.warn("Recurso não encontrado: {}", ex.getMessage());
        return errorResponse(ex.getMessage());
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Map<String, Object> handleAll(Exception ex) {
        log.error("Erro inesperado no servidor", ex);
        return errorResponse("Erro interno do servidor");
    }

    private Map<String, Object> errorResponse(String message) {
        Map<String, Object> error = new HashMap<>();
        error.put("timestamp", Instant.now().toString());
        error.put("error", message);
        error.put("traceId", MDC.get("traceId"));
        return error;
    }
}
