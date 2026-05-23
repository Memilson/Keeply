package com.keeply.agent.core;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.keeply.agent.model.ManifestChunk;

import java.sql.*;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class LocalDatabase {
    private final String url;
    private final ObjectMapper mapper = new ObjectMapper()
            .registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule())
            .findAndRegisterModules();

    public LocalDatabase(String path) {
        this.url = "jdbc:sqlite:" + path;
        init();
    }

    private void init() {
        try (Connection conn = connect(); Statement stmt = conn.createStatement()) {
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
        } catch (SQLException e) {
            throw new IllegalStateException("Falha ao inicializar SQLite", e);
        }
    }

    public Connection connect() throws SQLException {
        return DriverManager.getConnection(url);
    }

    public void saveFileCache(String path, long size, long lastModified, String hash, List<ManifestChunk> chunks) {
        String sql = "INSERT OR REPLACE INTO file_cache (path, size, last_modified, hash, chunks_json) VALUES (?, ?, ?, ?, ?)";
        try (Connection conn = connect(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
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

    public CachedFile getFileCache(String path) {
        String sql = "SELECT size, last_modified, hash, chunks_json FROM file_cache WHERE path = ?";
        try (Connection conn = connect(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
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

    public void addKnownChunks(Set<String> hashes) {
        String sql = "INSERT OR IGNORE INTO known_chunks (hash, sent_at) VALUES (?, ?)";
        try (Connection conn = connect(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            conn.setAutoCommit(false);
            long now = System.currentTimeMillis();
            for (String hash : hashes) {
                pstmt.setString(1, hash);
                pstmt.setLong(2, now);
                pstmt.addBatch();
            }
            pstmt.executeBatch();
            conn.commit();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public Set<String> getKnownChunks() {
        Set<String> hashes = new HashSet<>();
        try (Connection conn = connect(); Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT hash FROM known_chunks")) {
            while (rs.next()) {
                hashes.add(rs.getString("hash"));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return hashes;
    }
    
    public void clearCache() {
        try (Connection conn = connect(); Statement stmt = conn.createStatement()) {
            stmt.execute("DELETE FROM file_cache");
            stmt.execute("DELETE FROM known_chunks");
            stmt.execute("DELETE FROM settings WHERE key LIKE 'last_sync_%'");
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public String getLastSyncedSnapshot(UUID deviceId, String path) {
        String sql = "SELECT value FROM settings WHERE key = ?";
        try (Connection conn = connect(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, "last_sync_" + deviceId + "_" + path);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) return rs.getString("value");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    public void setLastSyncedSnapshot(UUID deviceId, String path, String snapshotId) {
        String sql = "INSERT OR REPLACE INTO settings (key, value) VALUES (?, ?)";
        try (Connection conn = connect(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, "last_sync_" + deviceId + "_" + path);
            pstmt.setString(2, snapshotId);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void reconstructIndex(com.keeply.agent.model.SnapshotManifest manifest) {
        try (Connection conn = connect()) {
            conn.setAutoCommit(false);
            
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
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public record CachedFile(long size, long lastModified, String hash, List<ManifestChunk> chunks) {}
}
