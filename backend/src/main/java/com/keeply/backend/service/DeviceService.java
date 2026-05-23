package com.keeply.backend.service;

import com.keeply.backend.dto.DeviceDtos;
import com.keeply.backend.model.Device;
import com.keeply.backend.repository.DeviceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class DeviceService {
    private final DeviceRepository devices;

    public DeviceService(DeviceRepository devices) {
        this.devices = devices;
    }

    @Transactional
    public DeviceDtos.DeviceResponse register(UUID userId, DeviceDtos.RegisterDeviceRequest request) {
        Device d = new Device();
        d.userId = userId;
        d.name = request.name();
        d.hostname = request.hostname();
        d.os = request.os();
        d.agentVersion = request.agentVersion();
        devices.save(d);
        return toResponse(d);
    }

    @Transactional(readOnly = true)
    public List<DeviceDtos.DeviceResponse> list(UUID userId) {
        return devices.findByUserId(userId).stream().map(this::toResponse).toList();
    }

    @Transactional
    public void heartbeat(UUID userId, UUID deviceId) {
        Device d = devices.findByIdAndUserId(deviceId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Device não encontrado"));
        d.lastSeenAt = Instant.now();
    }

    private DeviceDtos.DeviceResponse toResponse(Device d) {
        return new DeviceDtos.DeviceResponse(d.id, d.name, d.hostname, d.os, d.agentVersion, d.lastSeenAt);
    }
}
