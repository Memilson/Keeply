package com.keeply.agent.core;

import java.nio.file.Path;

@FunctionalInterface
public interface BackupProgressListener {
    BackupProgressListener NONE = progress -> {
    };

    void onProgress(BackupProgress progress);

    record BackupProgress(int percent, String message, Path sourceRoot) {
        public BackupProgress {
            percent = Math.max(0, Math.min(100, percent));
        }
    }
}
