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
        System.err.println(Instant.now() + " [ERROR] " + message + " -> " + error.getMessage());
        error.printStackTrace(System.err);
    }
}
