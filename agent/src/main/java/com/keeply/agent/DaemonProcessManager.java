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
            Path configPath = AgentPaths.resolveConfigPath();
            Path logPath = AgentPaths.resolveLogPath();
            Path pidPath = AgentPaths.resolvePidPath();

            Files.createDirectories(configPath.getParent());
            Files.createDirectories(logPath.getParent());
            Files.createDirectories(pidPath.getParent());
            
            if (isDaemonAlive(pidPath)) {
                log.accept("event=daemon.status status=already_running");
                return;
            }

            if (!Files.exists(configPath)) {
                log.accept("event=daemon.start status=skipped reason=config_missing path=" + configPath);
                return;
            }

            ProcessBuilder pb = new ProcessBuilder(buildJavaCommand(configPath));
            pb.redirectOutput(ProcessBuilder.Redirect.appendTo(logPath.toFile()));
            pb.redirectError(ProcessBuilder.Redirect.appendTo(logPath.toFile()));
            Process process = pb.start();

            Thread.sleep(700);
            if (!process.isAlive() && !isDaemonAlive(pidPath)) {
                log.accept("event=daemon.start status=failed log_path=" + logPath);
                return;
            }

            log.accept("event=daemon.start status=ok mode=background log_path=" + logPath);
        } catch (Exception e) {
            log.accept("event=daemon.start status=error message=\"" + e.getMessage() + "\"");
        }
    }

    private static List<String> buildJavaCommand(Path configPath) {
        String javaBin = Path.of(System.getProperty("java.home"), "bin", isWindows() ? "java.exe" : "java").toString();
        String classpath = System.getProperty("java.class.path");
        List<String> command = new ArrayList<>();
        command.add(javaBin);
        command.add("--enable-native-access=ALL-UNNAMED");
        command.add("-cp");
        command.add(classpath);
        command.add("com.keeply.agent.KeeplyAgentDaemonApp");
        command.add("--config");
        command.add(configPath.toString());
        return command;
    }

    private static boolean isDaemonAlive(Path pidPath) {
        if (!Files.exists(pidPath)) {
            return false;
        }

        try (java.nio.channels.FileChannel channel = java.nio.channels.FileChannel.open(pidPath, StandardOpenOption.WRITE);
             java.nio.channels.FileLock lock = channel.tryLock()) {
            
            if (lock != null) {
                // Se conseguimos o lock, significa que o daemon NÃO está rodando (ou morreu e o OS liberou o lock)
                return false;
            }
            // Se não conseguimos o lock, o daemon está rodando e segurando o lock
            return true;
        } catch (Exception e) {
            // Fallback para check de PID caso lock falhe por falta de permissão ou outro erro de IO
            return checkPidFallback(pidPath);
        }
    }

    private static boolean checkPidFallback(Path pidPath) {
        try {
            String value = Files.readString(pidPath).trim();
            if (value.isBlank()) return false;
            long pid = Long.parseLong(value);
            return ProcessHandle.of(pid).map(ProcessHandle::isAlive).orElse(false);
        } catch (Exception ignored) {
            return false;
        }
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }
}
