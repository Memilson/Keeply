package com.keeply.agent.core.db;

import com.keeply.agent.model.ChunkMetadata;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public final class KnownChunkRepository {
    private final DatabaseConnection database;

    public KnownChunkRepository(DatabaseConnection database) {
        this.database = database;
    }

    public List<String> page(String afterHash, int size) {
        List<String> result = new ArrayList<>(size);
        try (PreparedStatement statement = database.get().prepareStatement(
                "SELECT hash FROM known_chunks WHERE hash > ? ORDER BY hash LIMIT ?")) {
            statement.setString(1, afterHash == null ? "" : afterHash);
            statement.setInt(2, size);
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    result.add(rows.getString(1));
                }
            }
            return result;
        } catch (SQLException e) {
            throw new IllegalStateException("Falha ao paginar chunks conhecidos", e);
        }
    }

    public void addSession(Collection<ChunkMetadata> chunks) {
        try (PreparedStatement statement = database.get().prepareStatement(
                "INSERT OR IGNORE INTO backup_session_chunks (hash) VALUES (?)")) {
            for (ChunkMetadata chunk : chunks) {
                statement.setString(1, chunk.hash());
                statement.addBatch();
            }
            statement.executeBatch();
        } catch (SQLException e) {
            throw new IllegalStateException("Falha ao registrar chunks da sessão", e);
        }
    }

    public boolean isInSession(String hash) {
        try (PreparedStatement statement = database.get().prepareStatement(
                "SELECT 1 FROM backup_session_chunks WHERE hash = ?")) {
            statement.setString(1, hash);
            return statement.executeQuery().next();
        } catch (SQLException e) {
            throw new IllegalStateException("Falha ao consultar chunk da sessão", e);
        }
    }

    public boolean claimForSession(String hash) {
        try (PreparedStatement statement = database.get().prepareStatement(
                "INSERT OR IGNORE INTO backup_session_chunks (hash) VALUES (?)")) {
            statement.setString(1, hash);
            return statement.executeUpdate() == 1;
        } catch (SQLException e) {
            throw new IllegalStateException("Falha ao reservar chunk da sessão", e);
        }
    }

    public Optional<ChunkMetadata> find(String hash, long originalSize) {
        String sql = "SELECT hash, original_size, compressed_size FROM known_chunks " +
                "WHERE hash = ? AND original_size = ? AND compressed_size IS NOT NULL";
        try (PreparedStatement statement = database.get().prepareStatement(sql)) {
            statement.setString(1, hash);
            statement.setLong(2, originalSize);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next()
                        ? Optional.of(new ChunkMetadata(rows.getString(1), rows.getLong(2), rows.getLong(3)))
                        : Optional.empty();
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Falha ao consultar metadados de chunk", e);
        }
    }

    public void save(Collection<ChunkMetadata> chunks) {
        String sql = """
                INSERT INTO known_chunks (hash, sent_at, original_size, compressed_size, last_confirmed_at)
                VALUES (?, ?, ?, ?, ?)
                ON CONFLICT(hash) DO UPDATE SET original_size = excluded.original_size,
                    compressed_size = excluded.compressed_size, last_confirmed_at = excluded.last_confirmed_at
                """;
        inTransaction(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                long now = System.currentTimeMillis();
                for (ChunkMetadata chunk : chunks) {
                    statement.setString(1, chunk.hash());
                    statement.setLong(2, now);
                    statement.setLong(3, chunk.originalSize());
                    statement.setLong(4, chunk.compressedSize());
                    statement.setLong(5, now);
                    statement.addBatch();
                }
                statement.executeBatch();
            }
        }, "Falha ao salvar chunks conhecidos");
    }

    public void remove(Collection<String> hashes) {
        inTransaction(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "DELETE FROM known_chunks WHERE hash = ?")) {
                for (String hash : hashes) {
                    statement.setString(1, hash);
                    statement.addBatch();
                }
                statement.executeBatch();
            }
        }, "Falha ao remover chunks conhecidos");
    }

    private void inTransaction(SqlWork work, String message) {
        try {
            Connection connection = database.get();
            boolean autoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                work.run(connection);
                connection.commit();
            } catch (SQLException failure) {
                connection.rollback();
                throw failure;
            } finally {
                connection.setAutoCommit(autoCommit);
            }
        } catch (SQLException e) {
            throw new IllegalStateException(message, e);
        }
    }

    @FunctionalInterface
    private interface SqlWork {
        void run(Connection connection) throws SQLException;
    }
}
