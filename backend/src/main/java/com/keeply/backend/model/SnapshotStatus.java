/* Enumeração que define os possíveis estados de uma operação de criação de snapshot. */
package com.keeply.backend.model;

public enum SnapshotStatus {
    IN_PROGRESS,
    PROCESSING,
    COMPLETED,
    FAILED
}
