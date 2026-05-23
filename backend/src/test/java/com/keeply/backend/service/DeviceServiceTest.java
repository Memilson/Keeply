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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeviceServiceTest {
    @Mock
    private DeviceRepository deviceRepository;

    @InjectMocks
    private DeviceService deviceService;

    @Test
    void registerCreatesDeviceWithInstallationId() {
        UUID userId = UUID.randomUUID();
        when(deviceRepository.save(any(Device.class))).thenAnswer(inv -> {
            Device d = inv.getArgument(0);
            d.id = UUID.randomUUID();
            return d;
        });

        DeviceDtos.DeviceResponse response = deviceService.register(userId,
                new DeviceDtos.RegisterDeviceRequest("Meu Device", "host-1", "Linux", "1.0.0"));

        assertNotNull(response.id());
        ArgumentCaptor<Device> captor = ArgumentCaptor.forClass(Device.class);
        verify(deviceRepository).save(captor.capture());
        assertEquals("Meu Device", captor.getValue().name);
        assertEquals("Linux", captor.getValue().osName);
        assertNotNull(captor.getValue().deviceInstallationId);
    }
}
