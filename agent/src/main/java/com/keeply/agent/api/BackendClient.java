package com.keeply.agent.api;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.keeply.agent.auth.DeviceAuthStore;
import com.keeply.agent.model.ChunkMetadata;
import com.keeply.agent.model.DeviceSession;
import com.keeply.agent.model.ProtectionPlan;
import com.keeply.agent.model.SnapshotSummary;
import com.keeply.agent.model.StartedSnapshot;
import com.keeply.agent.model.TransferCredentials;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Backwards-compatible facade for agent/backend interactions.
 * Resource clients own endpoint payloads while HttpExecutor owns HTTP and refresh retry.
 */
public class BackendClient {
    private static final Logger log = LoggerFactory.getLogger(BackendClient.class);
    private final DeviceAuthStore authStore;
    private final AuthApiClient auth;
    private final DeviceApiClient devices;
    private final SnapshotApiClient snapshots;
    private final ChunkApiClient chunks;
    private final TransferSessionApiClient transfers;
    private volatile DeviceSession session;
    private volatile boolean sessionPersisted;

    public BackendClient(String baseUrl) {
        this(baseUrl, null);
    }

    public BackendClient(String baseUrl, DeviceAuthStore authStore) {
        this.authStore = authStore;
        ObjectMapper mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .findAndRegisterModules()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        HttpExecutor executor = new HttpExecutor(baseUrl, mapper, this::getSession, this::refreshSession);
        this.auth = new AuthApiClient(executor, mapper);
        this.devices = new DeviceApiClient(executor, mapper);
        this.snapshots = new SnapshotApiClient(executor, mapper);
        this.chunks = new ChunkApiClient(executor, mapper);
        this.transfers = new TransferSessionApiClient(executor, mapper);
    }

    public synchronized DeviceSession loginDevice(String email, String password, String installationId,
                                                   String hostname, String osName, String agentVersion) {
        String traceId = traceId();
        try {
            session = auth.loginDevice(email, password, installationId, hostname, osName, agentVersion, traceId);
            sessionPersisted = persistSession(session);
            return session;
        } catch (Exception e) {
            throw failure("Falha no login do device", traceId, e);
        }
    }

    public synchronized void setSession(DeviceSession session) {
        this.session = session;
        sessionPersisted = session != null;
    }

    public DeviceSession getSession() {
        return session;
    }

    public boolean hasPersistedSession() {
        return sessionPersisted;
    }

    public synchronized DeviceSession refreshSession() {
        if (authStore != null) {
            return authStore.updateLocked(saved -> {
                saved.filter(this::sameInstallation).ifPresent(latest -> session = latest);
                return refreshSessionRequest();
            });
        }
        return refreshSessionRequest();
    }

    public Optional<ProtectionPlan> getDevicePlan(UUID deviceId) {
        String traceId = traceId();
        try {
            return devices.getPlan(deviceId, traceId);
        } catch (Exception e) {
            throw failure("Falha ao obter plano do device", traceId, e);
        }
    }

    public ProtectionPlan upsertDevicePlan(UUID deviceId, ProtectionPlan.PlanType type, List<String> sources) {
        String traceId = traceId();
        try {
            return devices.upsertPlan(deviceId, type, sources, traceId);
        } catch (Exception e) {
            throw failure("Falha ao salvar plano do device", traceId, e);
        }
    }

    public StartedSnapshot startSnapshot(UUID deviceId, String sourcePath) {
        String traceId = traceId();
        try {
            return snapshots.start(deviceId, sourcePath, traceId);
        } catch (ApiException e) {
            if (e.getStatusCode() == 409 || (e.getError() != null && e.getError().contains("já existe um snapshot"))) {
                log.warn("[{}] Tentativa de snapshot duplicado para o dispositivo {}: {}", traceId, deviceId, e.getMessage());
                throw new IllegalStateException("Um backup já está em andamento para este dispositivo. Por favor, aguarde a conclusão ou falha do ciclo atual.", e);
            }
            throw failure("Falha ao iniciar snapshot", traceId, e);
        } catch (Exception e) {
            throw failure("Falha ao iniciar snapshot", traceId, e);
        }
    }

    public CheckChunksResult checkChunks(List<String> hashes) {
        String traceId = traceId();
        try {
            return chunks.checkChunks(hashes, traceId);
        } catch (Exception e) {
            throw failure("Falha ao verificar chunks", traceId, e);
        }
    }

    public void completeSnapshot(UUID snapshotId, UUID transferSessionId, long totalFiles,
                                 long totalOriginalSize, long totalCompressedSize) {
        String traceId = traceId();
        try {
            snapshots.complete(snapshotId, transferSessionId, totalFiles, totalOriginalSize,
                    totalCompressedSize, traceId);
        } catch (Exception e) {
            throw failure("Falha ao concluir snapshot " + snapshotId, traceId, e);
        }
    }

    public TransferCredentials renewTransferSession(UUID transferSessionId) {
        return transfer("/api/transfer-sessions/" + transferSessionId + "/renew");
    }

    public TransferCredentials startRestoreSession(UUID snapshotId) {
        return transfer("/api/snapshots/" + snapshotId + "/restore-sessions");
    }

    public void finishTransferSession(UUID transferSessionId) {
        finishTransfer("/api/transfer-sessions/" + transferSessionId + "/finish");
    }

    public void cancelTransferSession(UUID transferSessionId) {
        finishTransfer("/api/transfer-sessions/" + transferSessionId + "/cancel");
    }

    public void failSnapshot(UUID snapshotId, String errorMessage) {
        try {
            snapshots.fail(snapshotId, errorMessage, traceId());
        } catch (Exception ignored) {
            // Failure reporting must not hide the original backup failure.
        }
    }

    public List<SnapshotSummary> listSnapshots() {
        String traceId = traceId();
        try {
            return snapshots.list(traceId);
        } catch (Exception e) {
            throw failure("Falha ao listar snapshots", traceId, e);
        }
    }

    public long getStorageUsedBytes() {
        String traceId = traceId();
        try {
            return chunks.getStorageUsedBytes(traceId);
        } catch (Exception e) {
            throw failure("Falha ao obter armazenamento usado", traceId, e);
        }
    }

    public SnapshotFilePage listSnapshotFiles(UUID snapshotId, int page, int size, String search) {
        return listSnapshotFiles(snapshotId, page, size, search, null);
    }

    public SnapshotFilePage listSnapshotFiles(UUID snapshotId, int page, int size, String search, String prefix) {
        String traceId = traceId();
        try {
            return snapshots.listFiles(snapshotId, page, size, search, prefix, traceId);
        } catch (Exception e) {
            throw failure("Falha ao listar arquivos do snapshot", traceId, e);
        }
    }

    public List<SnapshotFileItem> listAllSnapshotFiles(UUID snapshotId, String search) {
        return listAllSnapshotFiles(snapshotId, search, null);
    }

    public List<SnapshotFileItem> listAllSnapshotFiles(UUID snapshotId, String search, String prefix) {
        List<SnapshotFileItem> items = new ArrayList<>();
        for (int pageNumber = 0;; pageNumber++) {
            SnapshotFilePage page = listSnapshotFiles(snapshotId, pageNumber, 200, search, prefix);
            items.addAll(page.items());
            if (page.items().isEmpty() || items.size() >= page.pagination().totalElements()) {
                return items;
            }
        }
    }

    private DeviceSession refreshSessionRequest() {
        if (session == null || blank(session.refreshToken()) || blank(session.deviceInstallationId())) {
            throw new IllegalStateException("Sessão inválida para refresh");
        }
        String traceId = traceId();
        try {
            session = auth.refresh(session, traceId);
            return session;
        } catch (Exception e) {
            throw failure("Falha ao renovar sessão", traceId, e);
        }
    }

    private TransferCredentials transfer(String path) {
        String traceId = traceId();
        try {
            return transfers.create(path, traceId);
        } catch (Exception e) {
            throw failure("Falha ao obter credencial de transferência", traceId, e);
        }
    }

    private void finishTransfer(String path) {
        String traceId = traceId();
        try {
            transfers.finish(path, traceId);
        } catch (Exception e) {
            throw failure("Falha ao encerrar sessão de transferência", traceId, e);
        }
    }

    private boolean sameInstallation(DeviceSession stored) {
        return session == null || Objects.equals(stored.deviceInstallationId(), session.deviceInstallationId());
    }

    private boolean persistSession(DeviceSession updated) {
        if (authStore != null) {
            try {
                authStore.save(updated);
                return true;
            } catch (IllegalStateException e) {
                // The API login is valid even when this machine cannot persist refresh credentials.
                log.warn("Login concluido, mas a sessao nao pode ser persistida localmente: {}", e.getMessage());
            }
        }
        return false;
    }

    private static String traceId() {
        return UUID.randomUUID().toString();
    }

    private static IllegalStateException failure(String message, String traceId, Exception cause) {
        return new IllegalStateException(message + " [Trace-ID: " + traceId + "]", cause);
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    public record ChunkUploadResult(String hash, boolean stored) {}

    public record CheckChunksResult(List<ChunkMetadata> existing, List<String> missing) {
        public CheckChunksResult {
            existing = existing == null ? List.of() : existing;
            missing = missing == null ? List.of() : missing;
        }

        public Set<String> existingHashes() {
            return existing.stream().map(ChunkMetadata::hash).collect(java.util.stream.Collectors.toSet());
        }
    }

    public record SnapshotFileItem(String path, long size, Instant lastModified) {}
    public record PageMetadata(long totalElements, int page, int size) {}
    public record SnapshotFilePage(List<SnapshotFileItem> items, PageMetadata pagination) {}
}
