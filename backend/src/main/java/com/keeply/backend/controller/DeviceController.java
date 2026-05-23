/* Controlador REST responsável pelo gerenciamento de dispositivos, permitindo o registro, listagem e envio de sinais de vitalidade (heartbeat). */
package com.keeply.backend.controller;

import com.keeply.backend.dto.DeviceDtos;
import com.keeply.backend.service.ProtectionPlanService;
import com.keeply.backend.service.DeviceService;
import com.keeply.backend.util.CurrentUser;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/devices")
public class DeviceController {
    private final DeviceService devices;
    private final ProtectionPlanService plans;

    public DeviceController(DeviceService devices, ProtectionPlanService plans) {
        this.devices = devices;
        this.plans = plans;
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

    @GetMapping("/{deviceId}/plan")
    public DeviceDtos.PlanResponse getPlan(@PathVariable UUID deviceId) {
        return plans.get(CurrentUser.get().userId(), deviceId);
    }

    @PutMapping("/{deviceId}/plan")
    public DeviceDtos.PlanResponse upsertPlan(@PathVariable UUID deviceId, @RequestBody DeviceDtos.PlanRequest request) {
        return plans.upsert(CurrentUser.get().userId(), deviceId, request);
    }
}
