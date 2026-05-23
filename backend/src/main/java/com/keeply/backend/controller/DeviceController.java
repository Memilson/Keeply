package com.keeply.backend.controller;

import com.keeply.backend.dto.DeviceDtos;
import com.keeply.backend.service.DeviceService;
import com.keeply.backend.util.CurrentUser;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/devices")
public class DeviceController {
    private final DeviceService devices;

    public DeviceController(DeviceService devices) {
        this.devices = devices;
    }

    @PostMapping("/register")
    public DeviceDtos.DeviceResponse register(@RequestBody DeviceDtos.RegisterDeviceRequest request) {
        return devices.register(CurrentUser.get().userId(), request);
    }

    @GetMapping
    public List<DeviceDtos.DeviceResponse> list() {
        return devices.list(CurrentUser.get().userId());
    }

    @PatchMapping("/{deviceId}/heartbeat")
    public void heartbeat(@PathVariable UUID deviceId) {
        devices.heartbeat(CurrentUser.get().userId(), deviceId);
    }
}
