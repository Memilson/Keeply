package com.keeply.agent.core;

import java.util.UUID;

public final class BackupSnapshotException extends RuntimeException {
    private final UUID snapshotId;
    private final UUID transferSessionId;
    private final String sourcePath;
    private final String userMessage;

    public BackupSnapshotException(UUID snapshotId, UUID transferSessionId, String sourcePath,
                                   String userMessage, Throwable cause) {
        super(userMessage, cause);
        this.snapshotId = snapshotId;
        this.transferSessionId = transferSessionId;
        this.sourcePath = sourcePath;
        this.userMessage = userMessage;
    }

    public UUID snapshotId() {
        return snapshotId;
    }

    public UUID transferSessionId() {
        return transferSessionId;
    }

    public String sourcePath() {
        return sourcePath;
    }

    public String userMessage() {
        return userMessage;
    }
}
