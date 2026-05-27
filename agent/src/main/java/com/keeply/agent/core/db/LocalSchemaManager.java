package com.keeply.agent.core.db;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

public final class LocalSchemaManager {
    public void initialize(Connection connection) throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("PRAGMA journal_mode=WAL;");
            stmt.execute("PRAGMA synchronous=NORMAL;");
            stmt.execute("""
                    CREATE TABLE IF NOT EXISTS file_cache (
                        source_path TEXT NOT NULL,
                        path TEXT NOT NULL,
                        size INTEGER NOT NULL,
                        last_modified INTEGER NOT NULL,
                        hash TEXT NOT NULL,
                        chunks_json TEXT NOT NULL,
                        PRIMARY KEY (source_path, path)
                    )
                    """);
            stmt.execute("""
                    CREATE TABLE IF NOT EXISTS known_chunks (
                        hash TEXT PRIMARY KEY,
                        sent_at INTEGER,
                        original_size INTEGER,
                        compressed_size INTEGER,
                        last_confirmed_at INTEGER
                    )
                    """);
            addColumnIfMissing(connection, "known_chunks", "original_size", "INTEGER");
            addColumnIfMissing(connection, "known_chunks", "compressed_size", "INTEGER");
            addColumnIfMissing(connection, "known_chunks", "last_confirmed_at", "INTEGER");
            stmt.execute("""
                    CREATE TABLE IF NOT EXISTS file_cache_chunks (
                        source_path TEXT NOT NULL,
                        file_path TEXT NOT NULL,
                        chunk_index INTEGER NOT NULL,
                        chunk_hash TEXT NOT NULL,
                        original_size INTEGER NOT NULL,
                        compressed_size INTEGER NOT NULL,
                        PRIMARY KEY (source_path, file_path, chunk_index)
                    )
                    """);
            stmt.execute("CREATE TABLE IF NOT EXISTS backup_session_chunks (hash TEXT PRIMARY KEY)");
            stmt.execute("""
                    CREATE TABLE IF NOT EXISTS backup_manifest_files (
                        path TEXT PRIMARY KEY,
                        size INTEGER,
                        last_modified INTEGER,
                        hash TEXT
                    )
                    """);
            stmt.execute("""
                    CREATE TABLE IF NOT EXISTS backup_manifest_chunks (
                        file_path TEXT,
                        chunk_index INTEGER,
                        chunk_hash TEXT,
                        original_size INTEGER,
                        compressed_size INTEGER,
                        FOREIGN KEY(file_path) REFERENCES backup_manifest_files(path)
                    )
                    """);
            stmt.execute("CREATE TABLE IF NOT EXISTS settings (key TEXT PRIMARY KEY, value TEXT)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_manifest_chunks_file_order ON backup_manifest_chunks(file_path, chunk_index)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_manifest_chunks_hash ON backup_manifest_chunks(chunk_hash)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_cache_chunks_file_order ON file_cache_chunks(source_path, file_path, chunk_index)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_cache_chunks_hash ON file_cache_chunks(chunk_hash)");
        }
    }

    private void addColumnIfMissing(Connection connection, String table, String column,
                                    String definition) throws SQLException {
        try (Statement stmt = connection.createStatement();
             ResultSet rows = stmt.executeQuery("PRAGMA table_info(" + table + ")")) {
            while (rows.next()) {
                if (column.equalsIgnoreCase(rows.getString("name"))) {
                    return;
                }
            }
        }
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("ALTER TABLE " + table + " ADD COLUMN " + column + " " + definition);
        }
    }
}
