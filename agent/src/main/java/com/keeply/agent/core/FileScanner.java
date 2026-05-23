package com.keeply.agent.core;

import java.io.IOException;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.Path;
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
            ".local/share/Trash"
    );

    private FileScanner() {
    }

    public static Stream<Path> scan(Path root) {
        try {
            return Files.walk(root, FileVisitOption.FOLLOW_LINKS)
                    .filter(path -> {
                        for (Path p : path) {
                            if (EXCLUDED_DIRS.contains(p.getFileName().toString())) {
                                return false;
                            }
                        }
                        return true;
                    })
                    .filter(Files::isRegularFile);
        } catch (IOException e) {
            throw new IllegalStateException("Falha ao escanear pasta: " + root, e);
        }
    }
}
