package com.keeply.agent.core.db;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

public final class BackupManifestRepository {
    private final DatabaseConnection database;

    public BackupManifestRepository(DatabaseConnection database) {
        this.database = database;
    }

    public void clear() {
        try (Statement statement = database.get().createStatement()) {
            statement.execute("DELETE FROM backup_manifest_chunks");
            statement.execute("DELETE FROM backup_manifest_files");
            statement.execute("DELETE FROM backup_session_chunks");
        } catch (SQLException e) {
            throw new IllegalStateException("Falha ao limpar manifesto temporário", e);
        }
    }

    public void addFile(String path, long size, long lastModified, String hash) {
        try (PreparedStatement statement = database.get().prepareStatement(
                "INSERT OR REPLACE INTO backup_manifest_files (path, size, last_modified, hash) VALUES (?, ?, ?, ?)")) {
            statement.setString(1, path);
            statement.setLong(2, size);
            statement.setLong(3, lastModified);
            statement.setString(4, hash);
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Falha ao adicionar arquivo ao manifesto", e);
        }
    }

    public void addChunk(String filePath, int index, String hash, long originalSize, long compressedSize) {
        try (PreparedStatement statement = database.get().prepareStatement(
                "INSERT INTO backup_manifest_chunks (file_path, chunk_index, chunk_hash, original_size, compressed_size) VALUES (?, ?, ?, ?, ?)")) {
            statement.setString(1, filePath);
            statement.setInt(2, index);
            statement.setString(3, hash);
            statement.setLong(4, originalSize);
            statement.setLong(5, compressedSize);
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Falha ao adicionar chunk ao manifesto", e);
        }
    }

    public long totalDistinctCompressedSize() {
        String sql = "SELECT COALESCE(SUM(compressed_size), 0) FROM " +
                "(SELECT chunk_hash, MAX(compressed_size) AS compressed_size " +
                "FROM backup_manifest_chunks GROUP BY chunk_hash)";
        try (Statement statement = database.get().createStatement(); ResultSet rows = statement.executeQuery(sql)) {
            return rows.next() ? rows.getLong(1) : 0L;
        } catch (SQLException e) {
            throw new IllegalStateException("Falha ao somar chunks do manifesto", e);
        }
    }
}
