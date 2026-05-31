package com.keeply.backend.service;

import com.keeply.backend.dto.SnapshotDtos;
import com.keeply.backend.model.Device;
import com.keeply.backend.model.SnapshotStatus;
import com.keeply.backend.repository.DeviceRepository;
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
    private ManifestParserService manifestParser;
    @Mock
    private TransferCredentialBroker transferBroker;

    private SnapshotService snapshotService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        snapshotService = new SnapshotService(snapshotRepository, deviceRepository, storage, manifestParser, transferBroker);
    }

    @Test
    void start_shouldThrowException_whenSnapshotAlreadyInProgress() {
        UUID userId = UUID.randomUUID();
        UUID deviceId = UUID.randomUUID();
        JwtPrincipal principal = new JwtPrincipal(userId, "test@example.com", deviceId);
        SnapshotDtos.StartSnapshotRequest request = new SnapshotDtos.StartSnapshotRequest(deviceId, "/test/path");

        Device device = new Device();
        device.id = deviceId;

        when(deviceRepository.findByIdAndUserId(deviceId, userId)).thenReturn(Optional.of(device));
        when(snapshotRepository.existsByDeviceIdAndStatusIn(eq(deviceId), any())).thenReturn(true);

        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> {
            snapshotService.start(principal, request);
        });

        assertEquals("Já existe um snapshot em execução para este dispositivo", exception.getMessage());
    }
}
