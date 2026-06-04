package com.keeply.backend.service;

import com.keeply.backend.dto.SnapshotDtos;
import com.keeply.backend.model.Device;
import com.keeply.backend.model.ProtectionPlan;
import com.keeply.backend.model.Snapshot;
import com.keeply.backend.model.SnapshotStatus;
import com.keeply.backend.model.TransferSession;
import com.keeply.backend.model.UserAccount;
import com.keeply.backend.repository.ChunkRepository;
import com.keeply.backend.repository.DeviceRepository;
import com.keeply.backend.repository.FileChunkRepository;
import com.keeply.backend.repository.ProtectionPlanRepository;
import com.keeply.backend.repository.RestoreJobRepository;
import com.keeply.backend.repository.SnapshotFileRepository;
import com.keeply.backend.repository.SnapshotRepository;
import com.keeply.backend.repository.TransferSessionRepository;
import com.keeply.backend.security.JwtPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SnapshotServiceTest {

    @Mock
    private SnapshotRepository snapshotRepository;
    @Mock
    private DeviceRepository deviceRepository;
    @Mock
    private ObjectStorageService storage;
    @Mock
    private SnapshotFileRepository snapshotFileRepository;
    @Mock
    private FileChunkRepository fileChunkRepository;
    @Mock
    private ChunkRepository chunkRepository;
    @Mock
    private TransferSessionRepository transferSessionRepository;
    @Mock
    private RestoreJobRepository restoreJobRepository;
    @Mock
    private ProtectionPlanRepository protectionPlanRepository;
    @Mock
    private ManifestParserService manifestParser;
    @Mock
    private SnapshotValidationService snapshotValidation;
    @Mock
    private TransferCredentialBroker transferBroker;

    private SnapshotService snapshotService;

    @BeforeEach
    void setUp() {
        snapshotService = new SnapshotService(
                snapshotRepository,
                deviceRepository,
                storage,
                snapshotFileRepository,
                fileChunkRepository,
                chunkRepository,
                transferSessionRepository,
                restoreJobRepository,
                protectionPlanRepository,
                manifestParser,
                snapshotValidation,
                transferBroker
        );
    }

    @Test
    void list_shouldOnlyReturnSnapshotsOwnedByTheUser() {
        UUID userId = UUID.randomUUID();
        Snapshot snapshot = new Snapshot();
        snapshot.id = UUID.randomUUID();
        snapshot.device = device(userId, UUID.randomUUID());

        when(snapshotRepository.findByDeviceUserIdOrderByCreatedAtDesc(eq(userId), any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of(snapshot), PageRequest.of(0, 10), 1));

        SnapshotDtos.PagedSnapshotResponse response = snapshotService.list(userId, 0, 10);

        assertEquals(1, response.items().size());
        assertEquals(snapshot.id, response.items().get(0).id());
    }

    @Test
    void complete_shouldFinalizeSynchronouslyWithoutOptionalValidation() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID deviceId = UUID.randomUUID();
        UUID snapshotId = UUID.randomUUID();
        UUID transferSessionId = UUID.randomUUID();
        String chunkHash = "a".repeat(64);
        JwtPrincipal principal = new JwtPrincipal(userId, "user@example.com", deviceId);
        Snapshot snapshot = snapshot(snapshotId, userId, deviceId, SnapshotStatus.IN_PROGRESS);
        TransferSession transferSession = new TransferSession();
        transferSession.id = transferSessionId;
        ProtectionPlan plan = new ProtectionPlan();
        plan.validationEnabled = false;
        ManifestParserService.ChunkReference reference =
                new ManifestParserService.ChunkReference(chunkHash, 10L, 8L, "ZSTD", 3);

        when(snapshotRepository.findByIdAndDeviceUserId(snapshotId, userId)).thenReturn(Optional.of(snapshot));
        when(transferBroker.processing(principal, transferSessionId, snapshotId)).thenReturn(transferSession);
        when(storage.exists("users/%s/manifests/%s.json.zst".formatted(userId, snapshotId))).thenReturn(true);
        when(protectionPlanRepository.findByDeviceId(deviceId)).thenReturn(Optional.of(plan));
        when(manifestParser.parseAndPersist("users/%s/manifests/%s.json.zst".formatted(userId, snapshotId), snapshot))
                .thenReturn(new ManifestParserService.ParsedManifest(1, Map.of(chunkHash, reference)));
        when(chunkRepository.findByUserIdAndHashIn(userId, List.of(chunkHash))).thenReturn(List.of());

        SnapshotDtos.SnapshotResponse response = snapshotService.complete(
                principal,
                snapshotId,
                new SnapshotDtos.CompleteSnapshotRequest(transferSessionId, 1, 10, 8)
        );

        assertEquals(SnapshotStatus.COMPLETED, response.status());
        verify(snapshotValidation, never()).validateUploadedChunks(any(), any());
        verify(chunkRepository).saveAll(any());
        verify(transferBroker).completeProcessing(transferSessionId, true, "Snapshot concluído");
    }

    @Test
    void complete_shouldFailWhenOptionalValidationFails() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID deviceId = UUID.randomUUID();
        UUID snapshotId = UUID.randomUUID();
        UUID transferSessionId = UUID.randomUUID();
        String chunkHash = "b".repeat(64);
        JwtPrincipal principal = new JwtPrincipal(userId, "user@example.com", deviceId);
        Snapshot snapshot = snapshot(snapshotId, userId, deviceId, SnapshotStatus.IN_PROGRESS);
        TransferSession transferSession = new TransferSession();
        transferSession.id = transferSessionId;
        ProtectionPlan plan = new ProtectionPlan();
        plan.validationEnabled = true;
        ManifestParserService.ChunkReference reference =
                new ManifestParserService.ChunkReference(chunkHash, 10L, 8L, "ZSTD", 3);

        when(snapshotRepository.findByIdAndDeviceUserId(snapshotId, userId)).thenReturn(Optional.of(snapshot));
        when(transferBroker.processing(principal, transferSessionId, snapshotId)).thenReturn(transferSession);
        when(storage.exists("users/%s/manifests/%s.json.zst".formatted(userId, snapshotId))).thenReturn(true);
        when(protectionPlanRepository.findByDeviceId(deviceId)).thenReturn(Optional.of(plan));
        when(manifestParser.parseAndPersist("users/%s/manifests/%s.json.zst".formatted(userId, snapshotId), snapshot))
                .thenReturn(new ManifestParserService.ParsedManifest(1, Map.of(chunkHash, reference)));
        when(chunkRepository.findByUserIdAndHashIn(userId, List.of(chunkHash))).thenReturn(List.of());
        when(snapshotFileRepository.findIdsBySnapshotId(snapshotId)).thenReturn(List.of());
        org.mockito.Mockito.doThrow(new IllegalStateException("Chunk ausente"))
                .when(snapshotValidation).validateUploadedChunks(userId, Map.of(chunkHash, reference));

        IllegalStateException error = assertThrows(IllegalStateException.class, () -> snapshotService.complete(
                principal,
                snapshotId,
                new SnapshotDtos.CompleteSnapshotRequest(transferSessionId, 1, 10, 8)
        ));

        assertEquals("Chunk ausente", error.getMessage());
        assertEquals(SnapshotStatus.FAILED, snapshot.status);
        verify(snapshotFileRepository).deleteBySnapshotId(snapshotId);
        verify(transferBroker).completeProcessing(transferSessionId, false, "Chunk ausente");
    }

    @Test
    void delete_shouldRemoveOwnedSnapshotRelationsAndManifest() {
        UUID userId = UUID.randomUUID();
        UUID snapshotId = UUID.randomUUID();
        UUID fileId = UUID.randomUUID();
        Snapshot snapshot = snapshot(snapshotId, userId, UUID.randomUUID(), SnapshotStatus.COMPLETED);
        snapshot.manifestKey = "users/%s/manifests/%s.json.zst".formatted(userId, snapshotId);

        when(snapshotRepository.findByIdAndDeviceUserId(snapshotId, userId)).thenReturn(Optional.of(snapshot));
        when(snapshotFileRepository.findIdsBySnapshotId(snapshotId)).thenReturn(List.of(fileId));

        snapshotService.delete(userId, snapshotId);

        verify(storage).delete(snapshot.manifestKey);
        verify(fileChunkRepository).deleteBySnapshotFileIdIn(List.of(fileId));
        verify(snapshotFileRepository).deleteBySnapshotId(snapshotId);
        verify(transferSessionRepository).deleteBySnapshotId(snapshotId);
        verify(restoreJobRepository).deleteBySnapshot_Id(snapshotId);
        verify(snapshotRepository).delete(snapshot);
    }

    @Test
    void delete_shouldRejectMissingOrForeignSnapshot() {
        UUID userId = UUID.randomUUID();
        UUID snapshotId = UUID.randomUUID();
        when(snapshotRepository.findByIdAndDeviceUserId(snapshotId, userId)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> snapshotService.delete(userId, snapshotId));

        verify(snapshotRepository, never()).delete(any());
        verify(storage, never()).delete(any());
    }

    private static Device device(UUID userId, UUID deviceId) {
        Device device = new Device();
        device.id = deviceId;
        device.user = new UserAccount();
        device.user.id = userId;
        return device;
    }

    private static Snapshot snapshot(UUID snapshotId, UUID userId, UUID deviceId, SnapshotStatus status) {
        Snapshot snapshot = new Snapshot();
        snapshot.id = snapshotId;
        snapshot.device = device(userId, deviceId);
        snapshot.status = status;
        return snapshot;
    }
}
