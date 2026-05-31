package com.keeply.agent.core.db;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.keeply.agent.model.ChunkMetadata;
import com.keeply.agent.model.ManifestChunk;

import java.io.InputStream;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public final class FileCacheRepository {
    private final DatabaseConnection database;
    private final ObjectMapper mapper;
    private final BackupManifestRepository manifest;

    public FileCacheRepository(DatabaseConnection database, ObjectMapper mapper, BackupManifestRepository manifest) {
        this.database = database;
        this.mapper = mapper;
        this.manifest = manifest;
    }

    public List<ChunkMetadata> chunksIfUnchanged(String sourcePath, String relativePath,
                                                  long size, long lastModified) {
        String sql = """
                SELECT c.chunk_hash, c.original_size, c.compressed_size
                FROM file_cache f JOIN file_cache_chunks c
                  ON c.source_path = f.source_path AND c.file_path = f.path
                WHERE f.source_path = ? AND f.path = ? AND f.size = ? AND f.last_modified = ?
                ORDER BY c.chunk_index
                """;
        List<ChunkMetadata> result = new ArrayList<>();
        try (PreparedStatement statement = database.get().prepareStatement(sql)) {
            statement.setString(1, sourcePath);
            statement.setString(2, relativePath);
            statement.setLong(3, size);
            statement.setLong(4, lastModified);
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    result.add(new ChunkMetadata(rows.getString(1), rows.getLong(2), rows.getLong(3)));
                }
            }
            return result;
        } catch (SQLException e) {
            throw new IllegalStateException("Falha ao ler chunks em cache", e);
        }
    }

    public void replaceFromManifest(String sourcePath) {
        inTransaction(connection -> {
            try (PreparedStatement clearChunks = connection.prepareStatement(
                    "DELETE FROM file_cache_chunks WHERE source_path = ?");
                 PreparedStatement clearFiles = connection.prepareStatement(
                         "DELETE FROM file_cache WHERE source_path = ?");
                 PreparedStatement copyFiles = connection.prepareStatement(
                         "INSERT INTO file_cache (source_path, path, size, last_modified, hash) " +
                                 "SELECT ?, path, size, last_modified, hash FROM backup_manifest_files");
                 PreparedStatement copyChunks = connection.prepareStatement(
                         "INSERT INTO file_cache_chunks (source_path, file_path, chunk_index, chunk_hash, original_size, compressed_size) " +
                                 "SELECT ?, file_path, chunk_index, chunk_hash, original_size, compressed_size FROM backup_manifest_chunks")) {
                clearChunks.setString(1, sourcePath);
                clearChunks.executeUpdate();
                clearFiles.setString(1, sourcePath);
                clearFiles.executeUpdate();
                copyFiles.setString(1, sourcePath);
                copyFiles.executeUpdate();
                copyChunks.setString(1, sourcePath);
                copyChunks.executeUpdate();
            }
        }, "Falha ao copiar manifesto para cache");
    }

    public void save(String sourcePath, String relativePath, long size, long lastModified,
                     String hash, List<ManifestChunk> chunks) {
        String fileSql = "INSERT OR REPLACE INTO file_cache " +
                "(source_path, path, size, last_modified, hash) VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement file = database.get().prepareStatement(fileSql);
             PreparedStatement deleteChunks = database.get().prepareStatement(
                     "DELETE FROM file_cache_chunks WHERE source_path = ? AND file_path = ?");
             PreparedStatement insertChunk = database.get().prepareStatement(
                     "INSERT INTO file_cache_chunks (source_path, file_path, chunk_index, chunk_hash, original_size, compressed_size) VALUES (?, ?, ?, ?, ?, ?)")) {
            file.setString(1, sourcePath);
            file.setString(2, relativePath);
            file.setLong(3, size);
            file.setLong(4, lastModified);
            file.setString(5, hash);
            file.executeUpdate();
            deleteChunks.setString(1, sourcePath);
            deleteChunks.setString(2, relativePath);
            deleteChunks.executeUpdate();
            if (chunks != null) {
                for (ManifestChunk chunk : chunks) {
                    insertChunk.setString(1, sourcePath);
                    insertChunk.setString(2, relativePath);
                    insertChunk.setInt(3, chunk.index());
                    insertChunk.setString(4, chunk.hash());
                    insertChunk.setLong(5, chunk.originalSize());
                    insertChunk.setLong(6, chunk.storedSize());
                    insertChunk.addBatch();
                }
                insertChunk.executeBatch();
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Falha ao salvar cache de arquivo", e);
        }
    }

    public int copyToManifestIfValid(String sourcePath, String relativePath, long size, long lastModified) {
        String fileSql = "SELECT hash FROM file_cache " +
                "WHERE source_path = ? AND path = ? AND size = ? AND last_modified = ?";
        try (PreparedStatement fileQuery = database.get().prepareStatement(fileSql)) {
            fileQuery.setString(1, sourcePath);
            fileQuery.setString(2, relativePath);
            fileQuery.setLong(3, size);
            fileQuery.setLong(4, lastModified);
            try (ResultSet file = fileQuery.executeQuery()) {
                if (!file.next()) {
                    return -1;
                }
                if (hasChunkOutsideSession(sourcePath, relativePath)) {
                    return -1;
                }
                manifest.addFile(relativePath, size, lastModified, file.getString(1));
                try (PreparedStatement copy = database.get().prepareStatement(
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

    public void clear(String sourcePath) {
        try (PreparedStatement files = database.get().prepareStatement(
                "DELETE FROM file_cache WHERE source_path = ?");
             PreparedStatement chunks = database.get().prepareStatement(
                     "DELETE FROM file_cache_chunks WHERE source_path = ?")) {
            files.setString(1, sourcePath);
            files.executeUpdate();
            chunks.setString(1, sourcePath);
            chunks.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Falha ao limpar cache de arquivo", e);
        }
    }

    public void reconstructIndex(String sourcePath, InputStream manifestStream) {
        String fileSql = "INSERT OR REPLACE INTO file_cache " +
                "(source_path, path, size, last_modified, hash) VALUES (?, ?, ?, ?, ?)";
        String chunkSql = "INSERT OR REPLACE INTO file_cache_chunks " +
                "(source_path, file_path, chunk_index, chunk_hash, original_size, compressed_size) VALUES (?, ?, ?, ?, ?, ?)";
        String knownSql = "INSERT OR REPLACE INTO known_chunks " +
                "(hash, sent_at, original_size, compressed_size, last_confirmed_at) VALUES (?, ?, ?, ?, ?)";
        clear(sourcePath);
        try (JsonParser parser = mapper.getFactory().createParser(manifestStream);
             PreparedStatement files = database.get().prepareStatement(fileSql);
             PreparedStatement cachedChunks = database.get().prepareStatement(chunkSql);
             PreparedStatement knownChunks = database.get().prepareStatement(knownSql)) {
            long now = System.currentTimeMillis();
            while (parser.nextToken() != null) {
                if (parser.currentToken() == JsonToken.FIELD_NAME && "files".equals(parser.currentName())) {
                    parseFiles(parser, sourcePath, now, files, cachedChunks, knownChunks);
                }
            }
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao reconstruir índice local", e);
        }
    }

    private void parseFiles(JsonParser parser, String sourcePath, long now, PreparedStatement files,
                            PreparedStatement cachedChunks, PreparedStatement knownChunks) throws Exception {
        parser.nextToken();
        while (parser.nextToken() != JsonToken.END_ARRAY) {
            String path = null;
            long size = 0;
            long modified = 0;
            String hash = null;
            while (parser.nextToken() != JsonToken.END_OBJECT) {
                if (parser.currentToken() != JsonToken.FIELD_NAME) {
                    continue;
                }
                String field = parser.currentName();
                parser.nextToken();
                switch (field) {
                    case "path" -> path = parser.getValueAsString();
                    case "size" -> size = parser.getLongValue();
                    case "lastModified" -> modified = Instant.parse(parser.getValueAsString()).toEpochMilli();
                    case "sha256" -> hash = parser.getValueAsString();
                    case "chunks" -> {
                        insertFile(files, sourcePath, path, size, modified, hash);
                        while (parser.nextToken() != JsonToken.END_ARRAY) {
                            ManifestChunk chunk = mapper.readValue(parser, ManifestChunk.class);
                            addReconstructedChunk(cachedChunks, knownChunks, sourcePath, path, now, chunk);
                        }
                        cachedChunks.executeBatch();
                        knownChunks.executeBatch();
                    }
                    default -> parser.skipChildren();
                }
            }
        }
    }

    private void insertFile(PreparedStatement files, String sourcePath, String path, long size,
                            long modified, String hash) throws SQLException {
        files.setString(1, sourcePath);
        files.setString(2, path);
        files.setLong(3, size);
        files.setLong(4, modified);
        files.setString(5, hash);
        files.executeUpdate();
    }

    private void addReconstructedChunk(PreparedStatement cached, PreparedStatement known, String sourcePath,
                                       String path, long now, ManifestChunk chunk) throws SQLException {
        cached.setString(1, sourcePath);
        cached.setString(2, path);
        cached.setInt(3, chunk.index());
        cached.setString(4, chunk.hash());
        cached.setLong(5, chunk.originalSize());
        cached.setLong(6, chunk.storedSize());
        cached.addBatch();
        known.setString(1, chunk.hash());
        known.setLong(2, now);
        known.setLong(3, chunk.originalSize());
        known.setLong(4, chunk.storedSize());
        known.setLong(5, now);
        known.addBatch();
    }

    private boolean hasChunkOutsideSession(String sourcePath, String relativePath) throws SQLException {
        try (PreparedStatement missing = database.get().prepareStatement(
                "SELECT 1 FROM file_cache_chunks c WHERE c.source_path = ? AND c.file_path = ? " +
                        "AND NOT EXISTS (SELECT 1 FROM backup_session_chunks s WHERE s.hash = c.chunk_hash) LIMIT 1")) {
            missing.setString(1, sourcePath);
            missing.setString(2, relativePath);
            return missing.executeQuery().next();
        }
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
