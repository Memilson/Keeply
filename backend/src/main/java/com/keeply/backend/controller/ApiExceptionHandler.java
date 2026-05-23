package com.keeply.backend.controller;

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
}
