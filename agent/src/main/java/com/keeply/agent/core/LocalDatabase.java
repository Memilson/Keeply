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
                // Refatorado para usar chave composta (source_path, path) para evitar colisões entre diferentes origens
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

                // Tabela para chunks conhecidos pelo servidor (evita check redundante)
                stmt.execute("""
                    CREATE TABLE IF NOT EXISTS known_chunks (
                        hash TEXT PRIMARY KEY,
                        sent_at INTEGER
                    )
                """);

                // Tabelas temporárias para construção do manifesto em backups grandes (evita OOM)
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

    public synchronized void clearBackupManifest() {
        try (Statement stmt = connect().createStatement()) {
            stmt.execute("DELETE FROM backup_manifest_files");
            stmt.execute("DELETE FROM backup_manifest_chunks");
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public synchronized void addManifestFile(String path, long size, long lastModified, String hash) {
        String sql = "INSERT OR REPLACE INTO backup_manifest_files (path, size, last_modified, hash) VALUES (?, ?, ?, ?)";
        try (PreparedStatement pstmt = connect().prepareStatement(sql)) {
            pstmt.setString(1, path);
            pstmt.setLong(2, size);
            pstmt.setLong(3, lastModified);
            pstmt.setString(4, hash);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public synchronized void addManifestChunk(String filePath, int index, String hash, long originalSize, long compressedSize) {
        String sql = "INSERT INTO backup_manifest_chunks (file_path, chunk_index, chunk_hash, original_size, compressed_size) VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement pstmt = connect().prepareStatement(sql)) {
            pstmt.setString(1, filePath);
            pstmt.setInt(2, index);
            pstmt.setString(3, hash);
            pstmt.setLong(4, originalSize);
            pstmt.setLong(5, compressedSize);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    /**
     * Retorna um Stream de FileManifest reconstruído do banco.
     * Importante: O chamador deve fechar o stream para fechar o ResultSet/Statement.
     */
    public java.util.stream.Stream<com.keeply.agent.model.FileManifest> getManifestFilesStream() {
        try {
            Connection conn = connect();
            String sql = "SELECT path, size, last_modified, hash FROM backup_manifest_files";
            Statement stmt = conn.createStatement();
            ResultSet rs = stmt.executeQuery(sql);

            return java.util.stream.StreamSupport.stream(new java.util.Spliterators.AbstractSpliterator<com.keeply.agent.model.FileManifest>(Long.MAX_VALUE, 0) {
                @Override
                public boolean tryAdvance(java.util.function.Consumer<? super com.keeply.agent.model.FileManifest> action) {
                    try {
                        if (!rs.next()) {
                            rs.close();
                            stmt.close();
                            return false;
                        }
                        String path = rs.getString("path");
                        long size = rs.getLong("size");
                        long mtime = rs.getLong("last_modified");
                        String hash = rs.getString("hash");
                        
                        List<ManifestChunk> chunks = getManifestChunks(path);
                        action.accept(new com.keeply.agent.model.FileManifest(path, size, java.time.Instant.ofEpochMilli(mtime), hash, chunks));
                        return true;
                    } catch (SQLException e) {
                        throw new RuntimeException(e);
                    }
                }
            }, false).onClose(() -> {
                try {
                    if (!rs.isClosed()) rs.close();
                    if (!stmt.isClosed()) stmt.close();
                } catch (SQLException ignored) {}
            });
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao ler manifesto do banco", e);
        }
    }

    private List<ManifestChunk> getManifestChunks(String filePath) throws SQLException {
        String sql = "SELECT chunk_index, chunk_hash, original_size, compressed_size FROM backup_manifest_chunks WHERE file_path = ? ORDER BY chunk_index";
        try (PreparedStatement pstmt = connect().prepareStatement(sql)) {
            pstmt.setString(1, filePath);
            try (ResultSet rs = pstmt.executeQuery()) {
                java.util.List<ManifestChunk> chunks = new java.util.ArrayList<>();
                while (rs.next()) {
                    chunks.add(new ManifestChunk(
                        rs.getInt("chunk_index"),
                        rs.getString("chunk_hash"),
                        rs.getLong("original_size"),
                        rs.getLong("compressed_size")
                    ));
                }
                return chunks;
            }
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

    public synchronized void saveFileCache(String sourcePath, String relativePath, long size, long lastModified, String hash, List<ManifestChunk> chunks) {
        String sql = "INSERT OR REPLACE INTO file_cache (source_path, path, size, last_modified, hash, chunks_json) VALUES (?, ?, ?, ?, ?, ?)";
        try (PreparedStatement pstmt = connect().prepareStatement(sql)) {
            pstmt.setString(1, sourcePath);
            pstmt.setString(2, relativePath);
            pstmt.setLong(3, size);
            pstmt.setLong(4, lastModified);
            pstmt.setString(5, hash);
            pstmt.setString(6, mapper.writeValueAsString(chunks));
            pstmt.executeUpdate();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public synchronized CachedFile getFileCache(String sourcePath, String relativePath) {
        String sql = "SELECT size, last_modified, hash, chunks_json FROM file_cache WHERE source_path = ? AND path = ?";
        try (PreparedStatement pstmt = connect().prepareStatement(sql)) {
            pstmt.setString(1, sourcePath);
            pstmt.setString(2, relativePath);
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
    
    public synchronized void clearCacheForPath(String sourcePath) {
        // Agora limpamos usando o source_path exato, o que é muito mais seguro e eficiente
        String sql = "DELETE FROM file_cache WHERE source_path = ?";
        try (PreparedStatement pstmt = connect().prepareStatement(sql)) {
            pstmt.setString(1, sourcePath);
            pstmt.executeUpdate();
            
            // Também removemos o marcador de último snapshot para forçar resync
            String sqlSettings = "DELETE FROM settings WHERE key LIKE ?";
            try (PreparedStatement pstmt2 = connect().prepareStatement(sqlSettings)) {
                pstmt2.setString(1, "last_sync_%_" + sourcePath);
                pstmt2.executeUpdate();
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public synchronized String getLastSyncedSnapshot(UUID deviceId, String sourcePath) {
        String sql = "SELECT value FROM settings WHERE key = ?";
        try (PreparedStatement pstmt = connect().prepareStatement(sql)) {
            pstmt.setString(1, "last_sync_" + deviceId + "_" + sourcePath);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) return rs.getString("value");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    public synchronized void setLastSyncedSnapshot(UUID deviceId, String sourcePath, String snapshotId) {
        String sql = "INSERT OR REPLACE INTO settings (key, value) VALUES (?, ?)";
        try (PreparedStatement pstmt = connect().prepareStatement(sql)) {
            pstmt.setString(1, "last_sync_" + deviceId + "_" + sourcePath);
            pstmt.setString(2, snapshotId);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public synchronized void reconstructIndex(String sourcePath, com.keeply.agent.model.SnapshotManifest manifest) {
        try {
            Connection conn = connect();
            boolean autoCommit = conn.getAutoCommit();
            conn.setAutoCommit(false);
            
            try {
                // Adicionar arquivos ao cache
                String fileSql = "INSERT OR REPLACE INTO file_cache (source_path, path, size, last_modified, hash, chunks_json) VALUES (?, ?, ?, ?, ?, ?)";
                try (PreparedStatement pstmt = conn.prepareStatement(fileSql)) {
                    for (com.keeply.agent.model.FileManifest f : manifest.files()) {
                        pstmt.setString(1, sourcePath);
                        pstmt.setString(2, f.path());
                        pstmt.setLong(3, f.size());
                        pstmt.setLong(4, f.lastModified().toEpochMilli());
                        pstmt.setString(5, f.sha256());
                        pstmt.setString(6, mapper.writeValueAsString(f.chunks()));
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
