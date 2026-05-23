package com.keeply.agent.core;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public final class FileScanner {
    private FileScanner() {}

    public static List<Path> scan(Path root) {
        try (var stream = Files.walk(root)) {
            return stream
                    .filter(Files::isRegularFile)
                    .sorted()
                    .toList();
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao escanear pasta: " + root, e);
        }
    }
}
