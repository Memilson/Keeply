package com.keeply.backend.service;

import com.keeply.backend.dto.DeviceDtos;
import com.keeply.backend.model.Device;
import com.keeply.backend.model.PlanType;
import com.keeply.backend.model.ProtectionPlan;
import com.keeply.backend.model.RetentionMode;
import com.keeply.backend.model.UserAccount;
import com.keeply.backend.repository.DeviceRepository;
import com.keeply.backend.repository.ProtectionPlanRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProtectionPlanServiceTest {
    @Mock
    private DeviceRepository deviceRepository;
    @Mock
    private ProtectionPlanRepository planRepository;
    @Mock
    private PasswordEncoder passwordEncoder;

    private ProtectionPlanService service;
    private UUID userId;
    private UUID deviceId;
    private Device device;

    @BeforeEach
    void setUp() {
        service = new ProtectionPlanService(deviceRepository, planRepository, passwordEncoder);
        userId = UUID.randomUUID();
        deviceId = UUID.randomUUID();
        device = new Device();
        device.id = deviceId;
        device.user = new UserAccount();
        device.user.id = userId;
        lenient().when(deviceRepository.findByIdAndUserId(deviceId, userId)).thenReturn(Optional.of(device));
        lenient().when(planRepository.findByDeviceId(deviceId)).thenReturn(Optional.empty());
        lenient().when(planRepository.save(any(ProtectionPlan.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void upsertPersistsKeepAllWithoutDaysAndPreservesScheduleValue() {
        DeviceDtos.PlanRequest request = new DeviceDtos.PlanRequest(
                PlanType.CUSTOM,
                List.of("/tmp/source"),
                true,
                false,
                "5 3 * * *",
                RetentionMode.KEEP_ALL,
                null,
                null
        );

        DeviceDtos.PlanResponse response = service.upsert(userId, deviceId, request);

        assertEquals("5 3 * * *", response.scheduleCron());
        assertEquals(RetentionMode.KEEP_ALL, response.retentionMode());
        assertNull(response.retentionDays());
    }

    @Test
    void upsertPersistsKeepDaysWithPositiveDays() {
        when(passwordEncoder.encode(any())).thenReturn("encoded");

        DeviceDtos.PlanRequest request = new DeviceDtos.PlanRequest(
                PlanType.CUSTOM,
                List.of("/tmp/source"),
                false,
                true,
                null,
                RetentionMode.KEEP_DAYS,
                30,
                "secret"
        );

        DeviceDtos.PlanResponse response = service.upsert(userId, deviceId, request);

        assertEquals(RetentionMode.KEEP_DAYS, response.retentionMode());
        assertEquals(30, response.retentionDays());
    }

    @Test
    void upsertRejectsInvalidRetentionCombinations() {
        DeviceDtos.PlanRequest keepAllWithDays = new DeviceDtos.PlanRequest(
                PlanType.CUSTOM,
                List.of("/tmp/source"),
                false,
                false,
                null,
                RetentionMode.KEEP_ALL,
                7,
                null
        );
        DeviceDtos.PlanRequest keepDaysWithoutDays = new DeviceDtos.PlanRequest(
                PlanType.CUSTOM,
                List.of("/tmp/source"),
                false,
                false,
                null,
                RetentionMode.KEEP_DAYS,
                null,
                null
        );

        assertThrows(IllegalArgumentException.class, () -> service.upsert(userId, deviceId, keepAllWithDays));
        assertThrows(IllegalArgumentException.class, () -> service.upsert(userId, deviceId, keepDaysWithoutDays));
    }
}
