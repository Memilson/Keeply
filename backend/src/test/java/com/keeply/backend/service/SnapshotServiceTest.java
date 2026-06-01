package com.keeply.backend.service;

import com.keeply.backend.dto.SnapshotDtos;
import com.keeply.backend.model.Device;
import com.keeply.backend.model.SnapshotStatus;
import com.keeply.backend.repository.DeviceRepository;
import com.keeply.backend.repository.FileChunkRepository;
import com.keeply.backend.repository.SnapshotFileRepository;
import com.keeply.backend.repository.SnapshotRepository;
import com.keeply.backend.security.JwtPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

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
    private ManifestParserService manifestParser;
    @Mock
    private TransferCredentialBroker transferBroker;

    private SnapshotService snapshotService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        snapshotService = new SnapshotService(snapshotRepository, deviceRepository, storage, snapshotFileRepository, fileChunkRepository, manifestParser, transferBroker);
    }

    @Test
    void start_shouldThrowException_whenSnapshotAlreadyInProgress() {
        // ... (existing test)
    }

    @Test
    void list_shouldOnlyReturnSnapshotsOwnedByTheUser() {
        UUID userA = UUID.randomUUID();
        UUID userB = UUID.randomUUID();
        
        com.keeply.backend.model.UserAccount ownerB = new com.keeply.backend.model.UserAccount();
        ownerB.id = userB;
        
        com.keeply.backend.model.Device deviceB = new com.keeply.backend.model.Device();
        deviceB.id = UUID.randomUUID();
        deviceB.user = ownerB;

        com.keeply.backend.model.Snapshot snapshotB = new com.keeply.backend.model.Snapshot();
        snapshotB.id = UUID.randomUUID();
        snapshotB.device = deviceB;

        // O SnapshotRepository.findByDeviceUserIdOrderByCreatedAtDesc deve retornar apenas snapshots do usuário B
        when(snapshotRepository.findByDeviceUserIdOrderByCreatedAtDesc(userA)).thenReturn(List.of());
        when(snapshotRepository.findByDeviceUserIdOrderByCreatedAtDesc(userB)).thenReturn(List.of(snapshotB));

        List<SnapshotDtos.SnapshotResponse> resultsA = snapshotService.list(userA);
        List<SnapshotDtos.SnapshotResponse> resultsB = snapshotService.list(userB);

        assertEquals(0, resultsA.size());
        assertEquals(1, resultsB.size());
        assertEquals(snapshotB.id, resultsB.get(0).id());
    }
}
