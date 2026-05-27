package com.keeply.agent.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.keeply.agent.core.db.BackupManifestRepository;
import com.keeply.agent.core.db.DatabaseConnection;
import com.keeply.agent.core.db.FileCacheRepository;
import com.keeply.agent.core.db.KnownChunkRepository;
import com.keeply.agent.core.db.ManifestWriter;
import com.keeply.agent.core.db.SnapshotSyncStateRepository;
import com.keeply.agent.model.ChunkMetadata;
import com.keeply.agent.model.ManifestChunk;

import java.io.InputStream;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Synchronized compatibility facade for the backup engine's local state.
 * Storage responsibilities live in repository classes under {@code core.db}.
 */
public class LocalDatabase implements AutoCloseable {
    private final DatabaseConnection database;
    private final KnownChunkRepository knownChunks;
    private final BackupManifestRepository manifest;
    private final ManifestWriter manifestWriter;
    private final FileCacheRepository fileCache;
    private final SnapshotSyncStateRepository syncState;

    public LocalDatabase(String path) {
        ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule()).findAndRegisterModules();
        this.database = new DatabaseConnection(path);
        this.knownChunks = new KnownChunkRepository(database);
        this.manifest = new BackupManifestRepository(database);
        this.manifestWriter = new ManifestWriter(database, mapper);
        this.fileCache = new FileCacheRepository(database, mapper, manifest);
        this.syncState = new SnapshotSyncStateRepository(database);
    }

    public synchronized void clearBackupManifest() {
        manifest.clear();
    }

    public synchronized List<String> getKnownChunksPage(String afterHash, int size) {
        return knownChunks.page(afterHash, size);
    }

    public synchronized void addSessionKnownChunks(Collection<ChunkMetadata> chunks) {
        knownChunks.addSession(chunks);
    }

    public synchronized boolean isKnownInSession(String hash) {
        return knownChunks.isInSession(hash);
    }

    public synchronized boolean claimChunkForSession(String hash) {
        return knownChunks.claimForSession(hash);
    }

    public synchronized Optional<ChunkMetadata> knownChunk(String hash, long originalSize) {
        return knownChunks.find(hash, originalSize);
    }

    public synchronized List<ChunkMetadata> cachedChunksIfUnchanged(String sourcePath, String relativePath,
                                                                     long size, long lastModified) {
        return fileCache.chunksIfUnchanged(sourcePath, relativePath, size, lastModified);
    }

    public synchronized long totalDistinctCompressedSize() {
        return manifest.totalDistinctCompressedSize();
    }

    public synchronized void writeManifestZstd(Path output, String snapshotId, String sourcePath) {
        manifestWriter.writeZstd(output, snapshotId, sourcePath);
    }

    public synchronized void saveManifestToCache(String sourcePath) {
        fileCache.replaceFromManifest(sourcePath);
    }

    public synchronized void addManifestFile(String path, long size, long lastModified, String hash) {
        manifest.addFile(path, size, lastModified, hash);
    }

    public synchronized void addManifestChunk(String filePath, int index, String hash,
                                              long originalSize, long compressedSize) {
        manifest.addChunk(filePath, index, hash, originalSize, compressedSize);
    }

    public synchronized Connection connect() throws SQLException {
        return database.get();
    }

    public synchronized void saveFileCache(String sourcePath, String relativePath, long size,
                                           long lastModified, String hash, List<ManifestChunk> chunks) {
        fileCache.save(sourcePath, relativePath, size, lastModified, hash, chunks);
    }

    public synchronized int copyCachedFileToManifestIfValid(String sourcePath, String relativePath,
                                                             long size, long lastModified) {
        return fileCache.copyToManifestIfValid(sourcePath, relativePath, size, lastModified);
    }

    public synchronized void addKnownChunks(Collection<ChunkMetadata> chunks) {
        knownChunks.save(chunks);
    }

    public synchronized void removeKnownChunks(Collection<String> hashes) {
        knownChunks.remove(hashes);
    }

    public synchronized void clearCacheForPath(String sourcePath) {
        fileCache.clear(sourcePath);
        syncState.clearForSource(sourcePath);
    }

    public synchronized String getLastSyncedSnapshot(UUID deviceId, String sourcePath) {
        return syncState.getLastSyncedSnapshot(deviceId, sourcePath);
    }

    public synchronized void setLastSyncedSnapshot(UUID deviceId, String sourcePath, String snapshotId) {
        syncState.setLastSyncedSnapshot(deviceId, sourcePath, snapshotId);
    }

    public synchronized void reconstructIndex(String sourcePath, InputStream manifestStream) {
        clearCacheForPath(sourcePath);
        fileCache.reconstructIndex(sourcePath, manifestStream);
    }

    @Override
    public synchronized void close() throws Exception {
        database.close();
    }
}
