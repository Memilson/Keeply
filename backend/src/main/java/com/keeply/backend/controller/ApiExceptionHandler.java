/* Intercepta exceções de validação e estado ilegal em toda a API, retornando respostas padronizadas de erro HTTP 400 (Bad Request). */
package com.keeply.backend.controller;

import com.keeply.backend.exception.ForbiddenException;
import com.keeply.backend.exception.NotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Map;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, Object> badRequest(RuntimeException ex) {
        return Map.of(
                "timestamp", Instant.now().toString(),
                "error", ex.getMessage()
        );
    }

    @ExceptionHandler(ForbiddenException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public Map<String, Object> forbidden(ForbiddenException ex) {
        return Map.of(
                "timestamp", Instant.now().toString(),
                "error", ex.getMessage()
        );
    }

    @ExceptionHandler(NotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Map<String, Object> notFound(NotFoundException ex) {
        return Map.of(
                "timestamp", Instant.now().toString(),
                "error", ex.getMessage()
        );
    }
}
