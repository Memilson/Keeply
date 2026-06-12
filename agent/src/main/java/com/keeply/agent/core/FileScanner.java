package com.keeply.agent.core;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

public final class FileScanner {
    private static final Set<String> DEFAULT_EXCLUDED_DIRS = Set.of(
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
            ".docker",
            "Trash",
            ".local/share/Trash",
            ".Agent"
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
        ExcludedPaths excludedPaths = excludedPaths();
        MutableStats stats = new MutableStats();
        try {
            Files.walkFileTree(normalizedRoot, Set.of(), Integer.MAX_VALUE, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    if (!dir.equals(normalizedRoot) && Files.isSymbolicLink(dir)) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    if (!dir.equals(normalizedRoot) && isExcluded(normalizedRoot, dir, excludedPaths)) {
                        stats.ignoredDirectories++;
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    if (!Files.isReadable(dir)) {
                        stats.unreadableFailures.add(ScanFailure.unreadableDirectory(dir));
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    if (Files.isSymbolicLink(file)) {
                        return FileVisitResult.CONTINUE;
                    }
                    if (attrs.isRegularFile()) {
                        if (!Files.isReadable(file)) {
                            stats.unreadableFailures.add(ScanFailure.unreadableFile(file));
                            return FileVisitResult.CONTINUE;
                        }
                        stats.totalBytes += attrs.size();
                        handler.accept(file);
                        stats.files++;
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path file, IOException exc) {
                    stats.unreadableFailures.add(new ScanFailure(file, exc));
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException exc) {
                    if (exc != null) {
                        stats.unreadableFailures.add(new ScanFailure(dir, exc));
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
            return new ScanStats(stats.files, stats.totalBytes, stats.ignoredDirectories, List.copyOf(stats.unreadableFailures));
        } catch (IOException e) {
            throw new IllegalStateException("Falha ao escanear pasta: " + root, e);
        }
    }

    private static boolean isExcluded(Path root, Path path, ExcludedPaths excludedPaths) {
        Path relative = root.relativize(path);
        String relativeName = relative.toString().replace('\\', '/');
        if (excludedPaths.relativePaths().contains(relativeName)) {
            return true;
        }
        for (Path component : relative) {
            if (excludedPaths.names().contains(component.toString())) {
                return true;
            }
        }
        return false;
    }

    private static ExcludedPaths excludedPaths() {
        Set<String> names = new LinkedHashSet<>();
        Set<String> relativePaths = new LinkedHashSet<>();
        for (String value : configuredExcludedDirs()) {
            String normalized = value.trim().replace('\\', '/');
            if (normalized.isEmpty()) {
                continue;
            }
            if (normalized.contains("/")) {
                relativePaths.add(normalized);
            } else {
                names.add(normalized);
            }
        }
        return new ExcludedPaths(Set.copyOf(names), Set.copyOf(relativePaths));
    }

    private static Set<String> configuredExcludedDirs() {
        Set<String> values = new LinkedHashSet<>(DEFAULT_EXCLUDED_DIRS);
        String configured = System.getProperty("keeply.agent.backup.exclude-dirs", "");
        for (String value : configured.split(",")) {
            if (!value.isBlank()) {
                values.add(value.trim());
            }
        }
        return values;
    }

    @FunctionalInterface
    public interface FileHandler {
        void accept(Path path) throws IOException;
    }

    public record ScanStats(long files, long totalBytes, long ignoredDirectories, List<ScanFailure> unreadableFailures) {
        public long prunedDirectories() {
            return ignoredDirectories;
        }

        public long unreadableEntries() {
            return unreadableFailures.size();
        }
    }

    public record ScanFailure(Path path, IOException cause) {
        static ScanFailure unreadableFile(Path path) {
            return new ScanFailure(path, new AccessDeniedException(path.toString(), null, "Unreadable file"));
        }

        static ScanFailure unreadableDirectory(Path path) {
            return new ScanFailure(path, new AccessDeniedException(path.toString(), null, "Unreadable directory"));
        }
    }

    private record ExcludedPaths(Set<String> names, Set<String> relativePaths) {
    }

    private static final class MutableStats {
        long files;
        long totalBytes;
        long ignoredDirectories;
        List<ScanFailure> unreadableFailures = new ArrayList<>();
    }
}
