package com.keeply.agent;

import com.keeply.agent.daemon.AgentPaths;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

final class DaemonProcessManager {
    private DaemonProcessManager() {
    }

    static void ensureDaemonRunning(Consumer<String> log) {
        try {
            Path configPath = AgentPaths.resolveDefaultConfigPath();
            Path dataDir = configPath.getParent();
            if (dataDir == null) {
                log.accept("Daemon: diretório de config inválido.");
                return;
            }
            Files.createDirectories(dataDir);

            Path pidPath = dataDir.resolve("daemon.pid");
            if (isDaemonAlive(pidPath)) {
                log.accept("Daemon já está ativo.");
                return;
            }

            if (!Files.exists(configPath)) {
                log.accept("Daemon não iniciado: config não encontrada em " + configPath);
                return;
            }

            Path logPath = dataDir.resolve("daemon.log");
            ProcessBuilder pb = new ProcessBuilder(buildJavaCommand(configPath));
            pb.redirectOutput(ProcessBuilder.Redirect.appendTo(logPath.toFile()));
            pb.redirectError(ProcessBuilder.Redirect.appendTo(logPath.toFile()));
            Process process = pb.start();

            Thread.sleep(700);
            if (!process.isAlive() && !isDaemonAlive(pidPath)) {
                log.accept("Daemon falhou ao iniciar. Verifique: " + logPath);
                return;
            }

            log.accept("Daemon iniciado em background. Logs: " + logPath);
        } catch (Exception e) {
            log.accept("Falha ao iniciar daemon automaticamente: " + e.getMessage());
        }
    }

    private static List<String> buildJavaCommand(Path configPath) {
        String javaBin = Path.of(System.getProperty("java.home"), "bin", isWindows() ? "java.exe" : "java").toString();
        String classpath = System.getProperty("java.class.path");
        List<String> command = new ArrayList<>();
        command.add(javaBin);
        command.add("-cp");
        command.add(classpath);
        command.add("com.keeply.agent.KeeplyAgentDaemonApp");
        command.add("--config");
        command.add(configPath.toString());
        return command;
    }

    private static boolean isDaemonAlive(Path pidPath) {
        try {
            if (!Files.exists(pidPath)) {
                return false;
            }
            String value = Files.readString(pidPath).trim();
            if (value.isBlank()) {
                return false;
            }
            long pid = Long.parseLong(value);
            Optional<ProcessHandle> handle = ProcessHandle.of(pid);
            boolean alive = handle.isPresent() && handle.get().isAlive();
            if (!alive) {
                Files.writeString(pidPath, "", StandardOpenOption.TRUNCATE_EXISTING);
            }
            return alive;
        } catch (Exception ignored) {
            return false;
        }
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }
}
