package com.keeply.agent.daemon;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Centraliza a resolução de caminhos do agente, respeitando padrões de SO
 * (XDG no Linux/Unix e APPDATA no Windows).
 */
public final class AgentPaths {
    private static final String APP_NAME = "keeply";
    private static final String OS = System.getProperty("os.name").toLowerCase();

    private AgentPaths() {
    }

    public static boolean isWindows() {
        return OS.contains("win");
    }

    private static Path getHomeDir() {
        return Paths.get(System.getProperty("user.home"));
    }

    /**
     * Diretório para arquivos de configuração.
     * Linux: $XDG_CONFIG_HOME/keeply ou ~/.config/keeply
     * Windows: %APPDATA%/keeply
     */
    public static Path resolveConfigDir() {
        Path base;
        if (isWindows()) {
            String appData = System.getenv("APPDATA");
            base = (appData != null) ? Paths.get(appData) : getHomeDir().resolve("AppData").resolve("Roaming");
        } else {
            String xdgConfig = System.getenv("XDG_CONFIG_HOME");
            base = (xdgConfig != null) ? Paths.get(xdgConfig) : getHomeDir().resolve(".config");
        }
        return base.resolve(APP_NAME).toAbsolutePath().normalize();
    }

    /**
     * Diretório para dados persistentes (bancos de dados, auth).
     * Linux: $XDG_DATA_HOME/keeply ou ~/.local/share/keeply
     * Windows: %LOCALAPPDATA%/keeply
     */
    public static Path resolveDataDir() {
        Path base;
        if (isWindows()) {
            String localAppData = System.getenv("LOCALAPPDATA");
            base = (localAppData != null) ? Paths.get(localAppData) : getHomeDir().resolve("AppData").resolve("Local");
        } else {
            String xdgData = System.getenv("XDG_DATA_HOME");
            base = (xdgData != null) ? Paths.get(xdgData) : getHomeDir().resolve(".local").resolve("share");
        }
        return base.resolve(APP_NAME).toAbsolutePath().normalize();
    }

    /**
     * Diretório para arquivos de estado e logs.
     * Linux: $XDG_STATE_HOME/keeply ou ~/.local/state/keeply
     * Windows: %LOCALAPPDATA%/keeply
     */
    public static Path resolveStateDir() {
        if (isWindows()) {
            return resolveDataDir();
        }
        String xdgState = System.getenv("XDG_STATE_HOME");
        Path base = (xdgState != null) ? Paths.get(xdgState) : getHomeDir().resolve(".local").resolve("state");
        return base.resolve(APP_NAME).toAbsolutePath().normalize();
    }

    /**
     * Diretório para arquivos em tempo de execução (PID).
     * Linux: $XDG_RUNTIME_DIR/keeply ou /tmp/keeply
     * Windows: %TEMP%/keeply
     */
    public static Path resolveRuntimeDir() {
        if (isWindows()) {
            String temp = System.getProperty("java.io.tmpdir");
            return Paths.get(temp, APP_NAME).toAbsolutePath().normalize();
        }
        String xdgRuntime = System.getenv("XDG_RUNTIME_DIR");
        Path base = (xdgRuntime != null) ? Paths.get(xdgRuntime) : Paths.get(System.getProperty("java.io.tmpdir"));
        return base.resolve(APP_NAME).toAbsolutePath().normalize();
    }

    public static Path resolveConfigPath() {
        return resolveConfigDir().resolve("agent.yaml");
    }

    public static Path resolveDefaultConfigPath() {
        return resolveConfigPath();
    }

    public static Path resolveLogPath() {
        return resolveStateDir().resolve("daemon.log");
    }

    public static Path resolvePidPath() {
        return resolveRuntimeDir().resolve("daemon.pid");
    }

    public static Path resolveMainDbPath() {
        return resolveDataDir().resolve("keeply_agent.db");
    }

    public static Path resolveUiDbPath() {
        return resolveDataDir().resolve("keeply_agent_ui.db");
    }

    public static Path resolveDeviceAuthPath() {
        return resolveDataDir().resolve("device-auth.json");
    }

    public static Path resolveDeviceIdPath() {
        return resolveDataDir().resolve("device-id.txt");
    }
}
