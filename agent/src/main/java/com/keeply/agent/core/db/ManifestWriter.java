package com.keeply.agent.core.db;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.luben.zstd.ZstdOutputStream;
import com.keeply.agent.core.ZstdCompressor;

import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Instant;

public final class ManifestWriter {
    private final DatabaseConnection database;
    private final ObjectMapper mapper;

    public ManifestWriter(DatabaseConnection database, ObjectMapper mapper) {
        this.database = database;
        this.mapper = mapper;
    }

    public void writeZstd(Path output, String snapshotId, String sourcePath) {
        String sql = """
                SELECT f.path, f.size, f.last_modified, f.hash,
                       c.chunk_index, c.chunk_hash, c.original_size, c.compressed_size
                FROM backup_manifest_files f LEFT JOIN backup_manifest_chunks c ON c.file_path = f.path
                ORDER BY f.path, c.chunk_index
                """;
        try (OutputStream out = Files.newOutputStream(output);
             ZstdOutputStream zstd = new ZstdOutputStream(out, ZstdCompressor.COMPRESSION_LEVEL);
             JsonGenerator json = mapper.getFactory().createGenerator(zstd);
             PreparedStatement statement = database.get().prepareStatement(sql);
             ResultSet rows = statement.executeQuery()) {
            json.writeStartObject();
            json.writeStringField("snapshotId", snapshotId);
            json.writeStringField("sourcePath", sourcePath);
            json.writeStringField("createdAt", Instant.now().toString());
            json.writeStringField("chunking", "CONTENT_DEFINED_MIN_1MB_AVG_4MB_MAX_8MB");
            json.writeStringField("compression", "ZSTD");
            json.writeStringField("hashAlgorithm", "SHA-256");
            json.writeArrayFieldStart("files");
            String openPath = null;
            while (rows.next()) {
                String path = rows.getString(1);
                if (!path.equals(openPath)) {
                    if (openPath != null) {
                        json.writeEndArray();
                        json.writeEndObject();
                    }
                    openPath = path;
                    json.writeStartObject();
                    json.writeStringField("path", path);
                    json.writeNumberField("size", rows.getLong(2));
                    json.writeStringField("lastModified", Instant.ofEpochMilli(rows.getLong(3)).toString());
                    json.writeStringField("sha256", rows.getString(4));
                    json.writeArrayFieldStart("chunks");
                }
                if (rows.getString(6) != null) {
                    json.writeStartObject();
                    json.writeNumberField("index", rows.getInt(5));
                    json.writeStringField("hash", rows.getString(6));
                    json.writeNumberField("originalSize", rows.getLong(7));
                    json.writeNumberField("compressedSize", rows.getLong(8));
                    json.writeEndObject();
                }
            }
            if (openPath != null) {
                json.writeEndArray();
                json.writeEndObject();
            }
            json.writeEndArray();
            json.writeEndObject();
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao escrever manifesto ZSTD", e);
        }
    }
}
