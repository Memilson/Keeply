package com.keeply.backend.service;

import com.keeply.backend.dto.DeviceDtos;
import com.keeply.backend.model.Device;
import com.keeply.backend.repository.DeviceRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DeviceServiceTest {
    @Mock
    private DeviceRepository deviceRepository;

    @InjectMocks
    private DeviceService deviceService;

    @Test
    void registerReusesExistingDeviceByUserAndHostname() {
        UUID userId = UUID.randomUUID();
        UUID existingId = UUID.randomUUID();
        Device existing = new Device();
        existing.id = existingId;
        existing.userId = userId;
        existing.hostname = "host-1";

        when(deviceRepository.findAllByUserIdAndHostnameOrderByLastSeenAtDescCreatedAtDesc(userId, "host-1"))
                .thenReturn(java.util.List.of(existing));
        when(deviceRepository.save(any(Device.class))).thenAnswer(inv -> inv.getArgument(0));

        DeviceDtos.DeviceResponse response = deviceService.register(userId,
                new DeviceDtos.RegisterDeviceRequest("Meu Device", "host-1", "Linux", "1.0.0"));

        assertEquals(existingId, response.id());
        ArgumentCaptor<Device> captor = ArgumentCaptor.forClass(Device.class);
        verify(deviceRepository).save(captor.capture());
        assertEquals("Meu Device", captor.getValue().name);
        assertEquals("Linux", captor.getValue().os);
    }
}
