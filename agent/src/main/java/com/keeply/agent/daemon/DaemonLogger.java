package com.keeply.agent.daemon;

import java.time.Instant;

public final class DaemonLogger {
    public void info(String message) {
        System.out.println(Instant.now() + " [INFO] " + message);
    }

    public void warn(String message) {
        System.out.println(Instant.now() + " [WARN] " + message);
    }

    public void error(String message, Throwable error) {
        // Para erros de estado/negócio (esperados), mostramos apenas a mensagem limpa.
        // Para erros inesperados, mantemos o stack trace para depuração.
        if (error instanceof IllegalStateException || error instanceof IllegalArgumentException) {
            System.err.println(Instant.now() + " [ERROR] " + message + ": " + error.getMessage());
            if (error.getCause() != null) {
                System.err.println("        Causa: " + error.getCause().getMessage());
            }
        } else {
            System.err.println(Instant.now() + " [ERROR] " + message + " -> " + error.getClass().getSimpleName() + ": " + error.getMessage());
            error.printStackTrace(System.err);
        }
    }
}
