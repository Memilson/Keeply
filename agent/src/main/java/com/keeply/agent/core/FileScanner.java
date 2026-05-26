package com.keeply.agent.core;

import java.io.IOException;
import java.nio.file.*;
import java.util.Set;
import java.util.stream.Stream;

public final class FileScanner {
    private static final Set<String> EXCLUDED_DIRS = Set.of(
            ".cache",
            ".gradle",
            ".m2",
            "node_modules",
            "target",
            "build",
            "dist",
            ".git",
            ".idea",
            ".vscode",
            "Trash",
            ".local/share/Trash",
            ".codex" // Ignorando diretório de metadados internos que mudam muito
    );

    private FileScanner() {
    }

    public static Stream<Path> scan(Path root) {
        try {
            Path normalizedRoot = root.toAbsolutePath().normalize();
            return Files.walk(root, FileVisitOption.FOLLOW_LINKS)
                    .filter(Files::isRegularFile)
                    .filter(path -> !isExcluded(normalizedRoot, path.toAbsolutePath().normalize()));
        } catch (IOException e) {
            throw new IllegalStateException("Falha ao escanear pasta: " + root, e);
        }
    }

    private static boolean isExcluded(Path root, Path path) {
        Path relative = root.relativize(path);
        for (Path component : relative) {
            if (EXCLUDED_DIRS.contains(component.toString())) {
                return true;
            }
        }
        return false;
    }
}
