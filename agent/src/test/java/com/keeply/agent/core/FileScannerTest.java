package com.keeply.agent.core;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FileScannerTest {

    @TempDir
    Path tempDir;

    @Test
    void walkIgnoresSymbolicLinkToFile() throws Exception {
        Path root = Files.createDirectory(tempDir.resolve("root"));
        Path external = Files.createDirectory(tempDir.resolve("external"));
        Path target = Files.writeString(external.resolve("outside.txt"), "outside");
        Path symlink = root.resolve("outside-link.txt");
        assumeSymlinkSupported(target, symlink);

        Files.writeString(root.resolve("inside.txt"), "inside");
        Files.createSymbolicLink(symlink, target);

        List<Path> files = new ArrayList<>();
        FileScanner.ScanStats stats = FileScanner.walk(root, files::add);

        assertEquals(List.of(root.resolve("inside.txt")), files);
        assertEquals(1, stats.files());
        assertEquals(0, stats.unreadableEntries());
    }

    @Test
    void walkDoesNotTraverseSymbolicLinkDirectory() throws Exception {
        Path root = Files.createDirectory(tempDir.resolve("root"));
        Path external = Files.createDirectory(tempDir.resolve("external"));
        Path nested = Files.createDirectory(external.resolve("nested"));
        Path target = Files.writeString(nested.resolve("outside.txt"), "outside");
        Path symlinkDir = root.resolve("external-link");
        assumeSymlinkSupported(target, symlinkDir);

        Files.writeString(root.resolve("inside.txt"), "inside");
        Files.createSymbolicLink(symlinkDir, external);

        List<Path> files = new ArrayList<>();
        FileScanner.ScanStats stats = FileScanner.walk(root, files::add);

        assertEquals(List.of(root.resolve("inside.txt")), files);
        assertEquals(1, stats.files());
        assertEquals(0, stats.unreadableEntries());
    }

    @Test
    void walkSkipsDockerDirectoryByDefault() throws Exception {
        Path root = Files.createDirectory(tempDir.resolve("root"));
        Path dockerDir = Files.createDirectories(root.resolve(".docker/desktop/vms/0/data"));
        Files.writeString(root.resolve("inside.txt"), "inside");
        Files.writeString(dockerDir.resolve("Docker.raw"), "vm-disk");

        List<Path> files = new ArrayList<>();
        FileScanner.ScanStats stats = FileScanner.walk(root, files::add);

        assertEquals(List.of(root.resolve("inside.txt")), files);
        assertEquals(1, stats.files());
        assertEquals(1, stats.ignoredDirectories());
    }

    private static void assumeSymlinkSupported(Path target, Path link) {
        try {
            Files.createSymbolicLink(link, target);
            Files.deleteIfExists(link);
        } catch (UnsupportedOperationException | IOException | SecurityException e) {
            Assumptions.assumeTrue(false, "symlink not supported in this environment");
        }
    }
}
