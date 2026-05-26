package com.keeply.agent.model;

import java.util.UUID;

public record StartedSnapshot(SnapshotSummary snapshot, TransferCredentials transfer) {
}
