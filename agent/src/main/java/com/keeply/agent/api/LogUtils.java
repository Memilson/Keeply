package com.keeply.agent.api;

import org.slf4j.Logger;

public final class LogUtils {
    private LogUtils() {}

    public static void logError(Logger logger, String message, Throwable error) {
        logger.error("{}: {}", message, error.getMessage());
        Throwable cause = error.getCause();
        int depth = 0;
        while (cause != null && depth < 5) {
            logger.error("  Causa {}: {}", depth + 1, cause.getMessage());
            cause = cause.getCause();
            depth++;
        }
        // Se não for um erro "esperado" (negócio), logamos o stacktrace completo em nível debug ou similar se necessário.
        // Mas por padrão mostramos a cadeia causal.
    }
}
