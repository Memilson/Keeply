package com.keeply.agent.core;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.keeply.agent.model.ManifestChunk;

import java.sql.*;
import java.io.OutputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.zip.GZIPOutputStream;

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
                stmt.execute("""
                    CREATE TABLE IF NOT EXISTS backup_session_chunks (
                        hash TEXT PRIMARY KEY
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
            stmt.execute("DELETE FROM backup_manifest_chunks");
            stmt.execute("DELETE FROM backup_manifest_files");
            stmt.execute("DELETE FROM backup_session_chunks");
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public synchronized List<String> getKnownChunksPage(String afterHash, int size) {
        String sql = "SELECT hash FROM known_chunks WHERE hash > ? ORDER BY hash LIMIT ?";
        List<String> result = new ArrayList<>(size);
        try (PreparedStatement pstmt = connect().prepareStatement(sql)) {
            pstmt.setString(1, afterHash == null ? "" : afterHash);
            pstmt.setInt(2, size);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) result.add(rs.getString(1));
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Falha ao paginar chunks conhecidos", e);
        }
        return result;
    }

    public synchronized void addSessionKnownChunks(Collection<String> hashes) {
        String sql = "INSERT OR IGNORE INTO backup_session_chunks (hash) VALUES (?)";
        try (PreparedStatement pstmt = connect().prepareStatement(sql)) {
            for (String hash : hashes) {
                pstmt.setString(1, hash);
                pstmt.addBatch();
            }
            pstmt.executeBatch();
        } catch (SQLException e) {
            throw new IllegalStateException("Falha ao registrar chunks da sessão", e);
        }
    }

    public synchronized boolean isKnownInSession(String hash) {
        try (PreparedStatement pstmt = connect().prepareStatement("SELECT 1 FROM backup_session_chunks WHERE hash = ?")) {
            pstmt.setString(1, hash);
            return pstmt.executeQuery().next();
        } catch (SQLException e) {
            throw new IllegalStateException("Falha ao consultar chunk da sessão", e);
        }
    }

    public synchronized boolean claimChunkForSession(String hash) {
        try (PreparedStatement pstmt = connect().prepareStatement("INSERT OR IGNORE INTO backup_session_chunks (hash) VALUES (?)")) {
            pstmt.setString(1, hash);
            return pstmt.executeUpdate() == 1;
        } catch (SQLException e) {
            throw new IllegalStateException("Falha ao reservar chunk da sessão", e);
        }
    }

    public synchronized long totalDistinctCompressedSize() {
        String sql = "SELECT COALESCE(SUM(compressed_size), 0) FROM (" +
                "SELECT chunk_hash, MAX(compressed_size) AS compressed_size FROM backup_manifest_chunks GROUP BY chunk_hash)";
        try (Statement stmt = connect().createStatement(); ResultSet rs = stmt.executeQuery(sql)) {
            return rs.next() ? rs.getLong(1) : 0L;
        } catch (SQLException e) {
            throw new IllegalStateException("Falha ao somar chunks do manifesto", e);
        }
    }

    public synchronized void writeManifestGzip(Path output, String snapshotId, String sourcePath) {
        String filesSql = "SELECT path, size, last_modified, hash FROM backup_manifest_files ORDER BY path";
        String chunksSql = "SELECT chunk_index, chunk_hash, original_size, compressed_size " +
                "FROM backup_manifest_chunks WHERE file_path = ? ORDER BY chunk_index";
        try (OutputStream out = Files.newOutputStream(output);
             GZIPOutputStream gzip = new GZIPOutputStream(out);
             JsonGenerator json = mapper.getFactory().createGenerator(gzip);
             PreparedStatement files = connect().prepareStatement(filesSql);
             PreparedStatement chunks = connect().prepareStatement(chunksSql);
             ResultSet fileRows = files.executeQuery()) {
            json.writeStartObject();
            json.writeStringField("snapshotId", snapshotId);
            json.writeStringField("sourcePath", sourcePath);
            json.writeStringField("createdAt", Instant.now().toString());
            json.writeStringField("chunking", "CONTENT_DEFINED_MIN_512KB_AVG_1MB_MAX_4MB");
            json.writeStringField("compression", "GZIP");
            json.writeStringField("hashAlgorithm", "SHA-256");
            json.writeArrayFieldStart("files");
            while (fileRows.next()) {
                json.writeStartObject();
                String path = fileRows.getString(1);
                json.writeStringField("path", path);
                json.writeNumberField("size", fileRows.getLong(2));
                json.writeStringField("lastModified", Instant.ofEpochMilli(fileRows.getLong(3)).toString());
                json.writeStringField("sha256", fileRows.getString(4));
                json.writeArrayFieldStart("chunks");
                chunks.setString(1, path);
                try (ResultSet chunkRows = chunks.executeQuery()) {
                    while (chunkRows.next()) {
                        json.writeStartObject();
                        json.writeNumberField("index", chunkRows.getInt(1));
                        json.writeStringField("hash", chunkRows.getString(2));
                        json.writeNumberField("originalSize", chunkRows.getLong(3));
                        json.writeNumberField("compressedSize", chunkRows.getLong(4));
                        json.writeEndObject();
                    }
                }
                json.writeEndArray();
                json.writeEndObject();
            }
            json.writeEndArray();
            json.writeEndObject();
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao escrever manifesto GZIP", e);
        }
    }

    public synchronized void saveManifestToCache(String sourcePath) {
        try {
            Connection conn = connect();
            boolean autoCommit = conn.getAutoCommit();
            conn.setAutoCommit(false);
            try (PreparedStatement clearChunks = conn.prepareStatement("DELETE FROM file_cache_chunks WHERE source_path = ?");
                 PreparedStatement clearFiles = conn.prepareStatement("DELETE FROM file_cache WHERE source_path = ?");
                 PreparedStatement copyFiles = conn.prepareStatement("INSERT INTO file_cache (source_path, path, size, last_modified, hash, chunks_json) SELECT ?, path, size, last_modified, hash, '[]' FROM backup_manifest_files");
                 PreparedStatement copyChunks = conn.prepareStatement("INSERT INTO file_cache_chunks (source_path, file_path, chunk_index, chunk_hash, original_size, compressed_size) SELECT ?, file_path, chunk_index, chunk_hash, original_size, compressed_size FROM backup_manifest_chunks")) {
                clearChunks.setString(1, sourcePath);
                clearChunks.executeUpdate();
                clearFiles.setString(1, sourcePath);
                clearFiles.executeUpdate();
                copyFiles.setString(1, sourcePath);
                copyFiles.executeUpdate();
                copyChunks.setString(1, sourcePath);
                copyChunks.executeUpdate();
                conn.commit();
            } finally {
                conn.setAutoCommit(autoCommit);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Falha ao copiar manifesto para cache", e);
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
            pstmt.setString(6, "[]");
            pstmt.executeUpdate();
            try (PreparedStatement delete = connect().prepareStatement("DELETE FROM file_cache_chunks WHERE source_path = ? AND file_path = ?");
                 PreparedStatement insert = connect().prepareStatement("INSERT INTO file_cache_chunks (source_path, file_path, chunk_index, chunk_hash, original_size, compressed_size) VALUES (?, ?, ?, ?, ?, ?)")) {
                delete.setString(1, sourcePath);
                delete.setString(2, relativePath);
                delete.executeUpdate();
                if (chunks != null) {
                    for (ManifestChunk chunk : chunks) {
                        insert.setString(1, sourcePath);
                        insert.setString(2, relativePath);
                        insert.setInt(3, chunk.index());
                        insert.setString(4, chunk.hash());
                        insert.setLong(5, chunk.originalSize());
                        insert.setLong(6, chunk.compressedSize());
                        insert.addBatch();
                    }
                    insert.executeBatch();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public synchronized int copyCachedFileToManifestIfValid(String sourcePath, String relativePath, long size, long lastModified) {
        String fileSql = "SELECT hash, chunks_json FROM file_cache WHERE source_path = ? AND path = ? AND size = ? AND last_modified = ?";
        try (PreparedStatement fileQuery = connect().prepareStatement(fileSql)) {
            fileQuery.setString(1, sourcePath);
            fileQuery.setString(2, relativePath);
            fileQuery.setLong(3, size);
            fileQuery.setLong(4, lastModified);
            try (ResultSet file = fileQuery.executeQuery()) {
                if (!file.next()) return -1;
                String fileHash = file.getString(1);
                String legacyJson = file.getString(2);
                try (PreparedStatement count = connect().prepareStatement("SELECT COUNT(*) FROM file_cache_chunks WHERE source_path = ? AND file_path = ?")) {
                    count.setString(1, sourcePath);
                    count.setString(2, relativePath);
                    try (ResultSet rows = count.executeQuery()) {
                        if (rows.next() && rows.getInt(1) == 0 && legacyJson != null && !"[]".equals(legacyJson)) {
                            List<ManifestChunk> legacy = mapper.readValue(legacyJson, new TypeReference<>() {});
                            saveFileCache(sourcePath, relativePath, size, lastModified, fileHash, legacy);
                        }
                    }
                }
                try (PreparedStatement missing = connect().prepareStatement(
                        "SELECT 1 FROM file_cache_chunks c WHERE c.source_path = ? AND c.file_path = ? " +
                                "AND NOT EXISTS (SELECT 1 FROM backup_session_chunks s WHERE s.hash = c.chunk_hash) LIMIT 1")) {
                    missing.setString(1, sourcePath);
                    missing.setString(2, relativePath);
                    if (missing.executeQuery().next()) return -1;
                }
                addManifestFile(relativePath, size, lastModified, fileHash);
                try (PreparedStatement copy = connect().prepareStatement(
                        "INSERT INTO backup_manifest_chunks (file_path, chunk_index, chunk_hash, original_size, compressed_size) " +
                                "SELECT file_path, chunk_index, chunk_hash, original_size, compressed_size FROM file_cache_chunks " +
                                "WHERE source_path = ? AND file_path = ? ORDER BY chunk_index")) {
                    copy.setString(1, sourcePath);
                    copy.setString(2, relativePath);
                    return copy.executeUpdate();
                }
            }
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao reutilizar cache de arquivo", e);
        }
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

    public synchronized void removeKnownChunks(Collection<String> hashes) {
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

    public synchronized void clearCacheForPath(String sourcePath) {
        // Agora limpamos usando o source_path exato, o que é muito mais seguro e eficiente
        String sql = "DELETE FROM file_cache WHERE source_path = ?";
        try (PreparedStatement pstmt = connect().prepareStatement(sql)) {
            pstmt.setString(1, sourcePath);
            pstmt.executeUpdate();
            try (PreparedStatement deleteChunks = connect().prepareStatement("DELETE FROM file_cache_chunks WHERE source_path = ?")) {
                deleteChunks.setString(1, sourcePath);
                deleteChunks.executeUpdate();
            }
            
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

    public synchronized void reconstructIndex(String sourcePath, InputStream manifestStream) {
        String fileSql = "INSERT OR REPLACE INTO file_cache (source_path, path, size, last_modified, hash, chunks_json) VALUES (?, ?, ?, ?, ?, '[]')";
        String cachedChunkSql = "INSERT OR REPLACE INTO file_cache_chunks (source_path, file_path, chunk_index, chunk_hash, original_size, compressed_size) VALUES (?, ?, ?, ?, ?, ?)";
        String knownChunkSql = "INSERT OR IGNORE INTO known_chunks (hash, sent_at) VALUES (?, ?)";
        clearCacheForPath(sourcePath);
        try (JsonParser parser = mapper.getFactory().createParser(manifestStream);
             PreparedStatement files = connect().prepareStatement(fileSql);
             PreparedStatement cachedChunks = connect().prepareStatement(cachedChunkSql);
             PreparedStatement knownChunks = connect().prepareStatement(knownChunkSql)) {
            long now = System.currentTimeMillis();
            while (parser.nextToken() != null) {
                if (parser.currentToken() == JsonToken.FIELD_NAME && "files".equals(parser.currentName())) {
                    parser.nextToken();
                    while (parser.nextToken() != JsonToken.END_ARRAY) {
                        String path = null;
                        long size = 0;
                        long lastModified = 0;
                        String sha256 = null;
                        while (parser.nextToken() != JsonToken.END_OBJECT) {
                            if (parser.currentToken() != JsonToken.FIELD_NAME) continue;
                            String name = parser.currentName();
                            parser.nextToken();
                            switch (name) {
                                case "path" -> path = parser.getValueAsString();
                                case "size" -> size = parser.getLongValue();
                                case "lastModified" -> lastModified = Instant.parse(parser.getValueAsString()).toEpochMilli();
                                case "sha256" -> sha256 = parser.getValueAsString();
                                case "chunks" -> {
                                    files.setString(1, sourcePath);
                                    files.setString(2, path);
                                    files.setLong(3, size);
                                    files.setLong(4, lastModified);
                                    files.setString(5, sha256);
                                    files.executeUpdate();
                                    while (parser.nextToken() != JsonToken.END_ARRAY) {
                                        ManifestChunk chunk = mapper.readValue(parser, ManifestChunk.class);
                                        cachedChunks.setString(1, sourcePath);
                                        cachedChunks.setString(2, path);
                                        cachedChunks.setInt(3, chunk.index());
                                        cachedChunks.setString(4, chunk.hash());
                                        cachedChunks.setLong(5, chunk.originalSize());
                                        cachedChunks.setLong(6, chunk.compressedSize());
                                        cachedChunks.addBatch();
                                        knownChunks.setString(1, chunk.hash());
                                        knownChunks.setLong(2, now);
                                        knownChunks.addBatch();
                                    }
                                    cachedChunks.executeBatch();
                                    knownChunks.executeBatch();
                                }
                                default -> parser.skipChildren();
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao reconstruir índice local", e);
        }
    }

}
