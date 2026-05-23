package com.keeply.agent.core;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.keeply.agent.model.ManifestChunk;

import java.sql.*;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class LocalDatabase implements AutoCloseable {
    private final String url;
    private final ObjectMapper mapper = new ObjectMapper()
            .registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule())
            .findAndRegisterModules();
    private Connection connection;

    public LocalDatabase(String path) {
        this.url = "jdbc:sqlite:" + path;
        init();
    }

    private synchronized void init() {
        try {
            this.connection = DriverManager.getConnection(url);
            try (Statement stmt = connection.createStatement()) {
                stmt.execute("PRAGMA journal_mode=WAL;");
                stmt.execute("PRAGMA synchronous=NORMAL;");
                
                // Tabela para controle de arquivos (cache de modificação)
                stmt.execute("""
                    CREATE TABLE IF NOT EXISTS file_cache (
                        path TEXT PRIMARY KEY,
                        size INTEGER,
                        last_modified INTEGER,
                        hash TEXT,
                        chunks_json TEXT
                    )
                """);

                // Tabela para chunks conhecidos pelo servidor (evita check redundante)
                stmt.execute("""
                    CREATE TABLE IF NOT EXISTS known_chunks (
                        hash TEXT PRIMARY KEY,
                        sent_at INTEGER
                    )
                """);

                // Metadados gerais
                stmt.execute("""
                    CREATE TABLE IF NOT EXISTS settings (
                        key TEXT PRIMARY KEY,
                        value TEXT
                    )
                """);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Falha ao inicializar SQLite", e);
        }
    }

    public synchronized Connection connect() throws SQLException {
        if (connection == null || connection.isClosed()) {
            this.connection = DriverManager.getConnection(url);
        }
        return connection;
    }

    @Override
    public synchronized void close() throws Exception {
        if (connection != null && !connection.isClosed()) {
            connection.close();
        }
    }

    public synchronized void saveFileCache(String path, long size, long lastModified, String hash, List<ManifestChunk> chunks) {
        String sql = "INSERT OR REPLACE INTO file_cache (path, size, last_modified, hash, chunks_json) VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement pstmt = connect().prepareStatement(sql)) {
            pstmt.setString(1, path);
            pstmt.setLong(2, size);
            pstmt.setLong(3, lastModified);
            pstmt.setString(4, hash);
            pstmt.setString(5, mapper.writeValueAsString(chunks));
            pstmt.executeUpdate();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public synchronized CachedFile getFileCache(String path) {
        String sql = "SELECT size, last_modified, hash, chunks_json FROM file_cache WHERE path = ?";
        try (PreparedStatement pstmt = connect().prepareStatement(sql)) {
            pstmt.setString(1, path);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    List<ManifestChunk> chunks = mapper.readValue(rs.getString("chunks_json"), new TypeReference<>() {});
                    return new CachedFile(rs.getLong("size"), rs.getLong("last_modified"), rs.getString("hash"), chunks);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public synchronized void addKnownChunks(Set<String> hashes) {
        String sql = "INSERT OR IGNORE INTO known_chunks (hash, sent_at) VALUES (?, ?)";
        try {
            Connection conn = connect();
            boolean autoCommit = conn.getAutoCommit();
            conn.setAutoCommit(false);
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                long now = System.currentTimeMillis();
                for (String hash : hashes) {
                    pstmt.setString(1, hash);
                    pstmt.setLong(2, now);
                    pstmt.addBatch();
                }
                pstmt.executeBatch();
                conn.commit();
            } finally {
                conn.setAutoCommit(autoCommit);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public synchronized void removeKnownChunks(Set<String> hashes) {
        String sql = "DELETE FROM known_chunks WHERE hash = ?";
        try {
            Connection conn = connect();
            boolean autoCommit = conn.getAutoCommit();
            conn.setAutoCommit(false);
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                for (String hash : hashes) {
                    pstmt.setString(1, hash);
                    pstmt.addBatch();
                }
                pstmt.executeBatch();
                conn.commit();
            } finally {
                conn.setAutoCommit(autoCommit);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public synchronized Set<String> getKnownChunks() {
        Set<String> hashes = new HashSet<>();
        try (Statement stmt = connect().createStatement();
             ResultSet rs = stmt.executeQuery("SELECT hash FROM known_chunks")) {
            while (rs.next()) {
                hashes.add(rs.getString("hash"));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return hashes;
    }
    
    public synchronized void clearCacheForPath(String path) {
        String sql = "DELETE FROM file_cache WHERE path LIKE ?";
        try (PreparedStatement pstmt = connect().prepareStatement(sql)) {
            pstmt.setString(1, path + "%");
            pstmt.executeUpdate();
            
            // Também removemos o marcador de último snapshot para forçar resync
            String sqlSettings = "DELETE FROM settings WHERE key LIKE ?";
            try (PreparedStatement pstmt2 = connect().prepareStatement(sqlSettings)) {
                pstmt2.setString(1, "%_" + path);
                pstmt2.executeUpdate();
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public synchronized String getLastSyncedSnapshot(UUID deviceId, String path) {
        String sql = "SELECT value FROM settings WHERE key = ?";
        try (PreparedStatement pstmt = connect().prepareStatement(sql)) {
            pstmt.setString(1, "last_sync_" + deviceId + "_" + path);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) return rs.getString("value");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    public synchronized void setLastSyncedSnapshot(UUID deviceId, String path, String snapshotId) {
        String sql = "INSERT OR REPLACE INTO settings (key, value) VALUES (?, ?)";
        try (PreparedStatement pstmt = connect().prepareStatement(sql)) {
            pstmt.setString(1, "last_sync_" + deviceId + "_" + path);
            pstmt.setString(2, snapshotId);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public synchronized void reconstructIndex(com.keeply.agent.model.SnapshotManifest manifest) {
        try {
            Connection conn = connect();
            boolean autoCommit = conn.getAutoCommit();
            conn.setAutoCommit(false);
            
            try {
                // Adicionar arquivos ao cache
                String fileSql = "INSERT OR REPLACE INTO file_cache (path, size, last_modified, hash, chunks_json) VALUES (?, ?, ?, ?, ?)";
                try (PreparedStatement pstmt = conn.prepareStatement(fileSql)) {
                    for (com.keeply.agent.model.FileManifest f : manifest.files()) {
                        pstmt.setString(1, f.path());
                        pstmt.setLong(2, f.size());
                        pstmt.setLong(3, f.lastModified().toEpochMilli());
                        pstmt.setString(4, f.sha256());
                        pstmt.setString(5, mapper.writeValueAsString(f.chunks()));
                        pstmt.addBatch();
                    }
                    pstmt.executeBatch();
                }

                // Adicionar chunks conhecidos
                String chunkSql = "INSERT OR IGNORE INTO known_chunks (hash, sent_at) VALUES (?, ?)";
                try (PreparedStatement pstmt = conn.prepareStatement(chunkSql)) {
                    long now = System.currentTimeMillis();
                    Set<String> allHashes = new HashSet<>();
                    for (com.keeply.agent.model.FileManifest f : manifest.files()) {
                        for (com.keeply.agent.model.ManifestChunk c : f.chunks()) {
                            allHashes.add(c.hash());
                        }
                    }
                    for (String h : allHashes) {
                        pstmt.setString(1, h);
                        pstmt.setLong(2, now);
                        pstmt.addBatch();
                    }
                    pstmt.executeBatch();
                }
                conn.commit();
            } finally {
                conn.setAutoCommit(autoCommit);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public record CachedFile(long size, long lastModified, String hash, List<ManifestChunk> chunks) {}
}
