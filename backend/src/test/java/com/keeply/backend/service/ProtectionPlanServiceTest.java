package com.keeply.backend.service;

import com.keeply.backend.dto.DeviceDtos;
import com.keeply.backend.exception.ForbiddenException;
import com.keeply.backend.model.Device;
import com.keeply.backend.model.PlanType;
import com.keeply.backend.model.ProtectionPlan;
import com.keeply.backend.repository.DeviceRepository;
import com.keeply.backend.repository.ProtectionPlanRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProtectionPlanServiceTest {
    @Mock
    private DeviceRepository devices;
    @Mock
    private ProtectionPlanRepository plans;

    @InjectMocks
    private ProtectionPlanService service;

    @Test
    void upsertCreatesOrUpdatesSinglePlanPerDevice() {
        UUID userId = UUID.randomUUID();
        UUID deviceId = UUID.randomUUID();
        Device d = new Device();
        d.id = deviceId;
        d.userId = userId;

        ProtectionPlan existing = new ProtectionPlan();
        existing.id = UUID.randomUUID();
        existing.deviceId = deviceId;

        when(devices.findById(deviceId)).thenReturn(Optional.of(d));
        when(plans.findByDeviceId(deviceId)).thenReturn(Optional.of(existing));
        when(plans.save(any(ProtectionPlan.class))).thenAnswer(inv -> inv.getArgument(0));

        service.upsert(userId, deviceId, new DeviceDtos.PlanRequest(PlanType.CUSTOM, List.of("/tmp/a", "/tmp/b", "/tmp/a")));

        ArgumentCaptor<ProtectionPlan> captor = ArgumentCaptor.forClass(ProtectionPlan.class);
        verify(plans).save(captor.capture());
        assertEquals(existing.id, captor.getValue().id);
        assertEquals(2, captor.getValue().sources.size());
        verify(plans, times(1)).save(any(ProtectionPlan.class));
    }

    @Test
    void blocksAccessToOtherUserDevice() {
        UUID userId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID deviceId = UUID.randomUUID();

        Device d = new Device();
        d.id = deviceId;
        d.userId = ownerId;
        when(devices.findById(deviceId)).thenReturn(Optional.of(d));

        assertThrows(ForbiddenException.class, () ->
                service.get(userId, deviceId));
    }

    @Test
    void rejectsInvalidSources() {
        UUID userId = UUID.randomUUID();
        UUID deviceId = UUID.randomUUID();
        Device d = new Device();
        d.id = deviceId;
        d.userId = userId;
        when(devices.findById(deviceId)).thenReturn(Optional.of(d));

        assertThrows(IllegalArgumentException.class,
                () -> service.upsert(userId, deviceId, new DeviceDtos.PlanRequest(PlanType.CUSTOM, List.of(" "))));
    }
}
