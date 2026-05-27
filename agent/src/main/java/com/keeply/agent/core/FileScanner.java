package com.keeply.agent.core;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
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
        List<Path> files = new ArrayList<>();
        walk(root, files::add);
        return files.stream();
    }

    public static ScanStats walk(Path root, FileHandler handler) {
        Path normalizedRoot = root.toAbsolutePath().normalize();
        MutableStats stats = new MutableStats();
        try {
            Files.walkFileTree(normalizedRoot, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    if (!dir.equals(normalizedRoot) && isExcluded(normalizedRoot, dir)) {
                        stats.prunedDirectories++;
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    if (attrs.isRegularFile() && !attrs.isSymbolicLink()) {
                        handler.accept(file);
                        stats.files++;
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path file, IOException exc) {
                    stats.unreadableEntries++;
                    return FileVisitResult.CONTINUE;
                }
            });
            return new ScanStats(stats.files, stats.prunedDirectories, stats.unreadableEntries);
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

    @FunctionalInterface
    public interface FileHandler {
        void accept(Path path) throws IOException;
    }

    public record ScanStats(long files, long prunedDirectories, long unreadableEntries) {
    }

    private static final class MutableStats {
        long files;
        long prunedDirectories;
        long unreadableEntries;
    }
}
