package com.keeply.agent.core;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.EnumSet;
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
        Stream.Builder<Path> builder = Stream.builder();
        try {
            Files.walkFileTree(root, EnumSet.of(FileVisitOption.FOLLOW_LINKS), Integer.MAX_VALUE, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    if (EXCLUDED_DIRS.contains(dir.getFileName().toString())) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    if (attrs.isRegularFile()) {
                        builder.add(file);
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path file, IOException exc) {
                    // Silenciosamente ignora arquivos que não podem ser acessados ou desapareceram
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            throw new IllegalStateException("Falha ao escanear pasta: " + root, e);
        }
        return builder.build();
    }
}
