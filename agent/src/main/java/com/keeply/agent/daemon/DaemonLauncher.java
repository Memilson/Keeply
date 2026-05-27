package com.keeply.agent.daemon;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public final class DaemonLauncher {
    private DaemonLauncher() {
    }

    public static void ensureRunning(Consumer<String> log) {
        ensureRunning(log, false);
    }

    public static void ensureRunning(Consumer<String> log, boolean forceRestart) {
        try {
            Path configPath = AgentPaths.resolveConfigPath();
            Path logPath = AgentPaths.resolveLogPath();
            Path pidPath = AgentPaths.resolvePidPath();
            Files.createDirectories(configPath.getParent());
            Files.createDirectories(logPath.getParent());
            Files.createDirectories(pidPath.getParent());

            if (isDaemonAlive(pidPath)) {
                if (forceRestart) {
                    log.accept("event=daemon.status status=restarting");
                    killDaemon(pidPath);
                } else {
                    log.accept("event=daemon.status status=already_running");
                    return;
                }
            }
            if (!Files.exists(configPath)) {
                log.accept("event=daemon.start status=skipped reason=config_missing path=" + configPath);
                return;
            }
            ProcessBuilder processBuilder = new ProcessBuilder(buildJavaCommand(configPath));
            processBuilder.redirectOutput(ProcessBuilder.Redirect.appendTo(logPath.toFile()));
            processBuilder.redirectError(ProcessBuilder.Redirect.appendTo(logPath.toFile()));
            Process process = processBuilder.start();
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

    private static void killDaemon(Path pidPath) {
        try {
            String content = Files.readString(pidPath).trim();
            if (content.isBlank()) return;
            long pid = Long.parseLong(content);
            ProcessHandle.of(pid).ifPresent(ph -> {
                ph.destroy();
                try {
                    ph.onExit().get(5, java.util.concurrent.TimeUnit.SECONDS);
                } catch (Exception ignored) {
                    ph.destroyForcibly();
                }
            });
            Files.deleteIfExists(pidPath);
        } catch (Exception e) {
            // Ignore kill errors
        }
    }

    private static List<String> buildJavaCommand(Path configPath) {
        String executable = Path.of(System.getProperty("java.home"), "bin",
                AgentPaths.isWindows() ? "java.exe" : "java").toString();
        List<String> command = new ArrayList<>();
        command.add(executable);
        command.add("--enable-native-access=ALL-UNNAMED");
        command.add("-Xmx128m");
        command.add("-cp");
        command.add(System.getProperty("java.class.path"));
        command.add("com.keeply.agent.KeeplyAgentDaemonApp");
        command.add("--config");
        command.add(configPath.toString());
        return command;
    }

    private static boolean isDaemonAlive(Path pidPath) {
        if (!Files.exists(pidPath)) {
            return false;
        }
        try (var channel = java.nio.channels.FileChannel.open(pidPath, StandardOpenOption.WRITE);
             var lock = channel.tryLock()) {
            return lock == null;
        } catch (Exception e) {
            return processIsAlive(pidPath);
        }
    }

    private static boolean processIsAlive(Path pidPath) {
        try {
            String value = Files.readString(pidPath).trim();
            return !value.isBlank() && ProcessHandle.of(Long.parseLong(value))
                    .map(ProcessHandle::isAlive).orElse(false);
        } catch (Exception ignored) {
            return false;
        }
    }
}
