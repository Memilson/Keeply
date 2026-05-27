package com.keeply.agent.core.db;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

public final class SnapshotSyncStateRepository {
    private final DatabaseConnection database;

    public SnapshotSyncStateRepository(DatabaseConnection database) {
        this.database = database;
    }

    public String getLastSyncedSnapshot(UUID deviceId, String sourcePath) {
        try (PreparedStatement statement = database.get().prepareStatement(
                "SELECT value FROM settings WHERE key = ?")) {
            statement.setString(1, key(deviceId, sourcePath));
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next() ? rows.getString(1) : null;
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Falha ao consultar estado de sincronização", e);
        }
    }

    public void setLastSyncedSnapshot(UUID deviceId, String sourcePath, String snapshotId) {
        try (PreparedStatement statement = database.get().prepareStatement(
                "INSERT OR REPLACE INTO settings (key, value) VALUES (?, ?)")) {
            statement.setString(1, key(deviceId, sourcePath));
            statement.setString(2, snapshotId);
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Falha ao salvar estado de sincronização", e);
        }
    }

    public void clearForSource(String sourcePath) {
        try (PreparedStatement statement = database.get().prepareStatement(
                "DELETE FROM settings WHERE key LIKE ?")) {
            statement.setString(1, "last_sync_%_" + sourcePath);
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Falha ao limpar estado de sincronização", e);
        }
    }

    private static String key(UUID deviceId, String sourcePath) {
        return "last_sync_" + deviceId + "_" + sourcePath;
    }
}
