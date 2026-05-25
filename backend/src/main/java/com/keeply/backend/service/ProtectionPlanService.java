package com.keeply.backend.service;

import com.keeply.backend.dto.DeviceDtos;
import com.keeply.backend.exception.ForbiddenException;
import com.keeply.backend.exception.NotFoundException;
import com.keeply.backend.model.Device;
import com.keeply.backend.model.PlanType;
import com.keeply.backend.model.ProtectionPlan;
import com.keeply.backend.repository.DeviceRepository;
import com.keeply.backend.repository.ProtectionPlanRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class ProtectionPlanService {
    private final DeviceRepository devices;
    private final ProtectionPlanRepository plans;

    public ProtectionPlanService(DeviceRepository devices, ProtectionPlanRepository plans) {
        this.devices = devices;
        this.plans = plans;
    }

    @Transactional(readOnly = true)
    public DeviceDtos.PlanResponse get(UUID userId, UUID deviceId) {
        ensureOwnedDevice(userId, deviceId);
        ProtectionPlan plan = plans.findByDeviceId(deviceId)
                .orElseThrow(() -> new NotFoundException("Plano de proteção não encontrado"));
        return toResponse(plan);
    }

    @Transactional
    public DeviceDtos.PlanResponse upsert(UUID userId, UUID deviceId, DeviceDtos.PlanRequest request) {
        Device device = ensureOwnedDevice(userId, deviceId);
        validatePlanRequest(request);

        ProtectionPlan plan = plans.findByDeviceId(deviceId).orElseGet(ProtectionPlan::new);
        plan.device = device;
        plan.planType = request.planType();
        plan.sources = normalizeSources(request.sources());

        if (plan.planType == PlanType.DEFAULT) {
            if (plan.sources.size() != 1) {
                throw new IllegalArgumentException("Plano DEFAULT deve conter exatamente uma origem");
            }
        }

        plans.save(plan);
        return toResponse(plan);
    }

    private Device ensureOwnedDevice(UUID userId, UUID deviceId) {
        // Usa o repositório para verificar a posse diretamente no banco, 
        // evitando problemas de NullPointerException com Proxies do Hibernate (FetchType.LAZY)
        return devices.findByIdAndUserId(deviceId, userId)
                .orElseThrow(() -> new ForbiddenException("Acesso negado ou Device não encontrado"));
    }

    private void validatePlanRequest(DeviceDtos.PlanRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Corpo da requisição é obrigatório");
        }
        if (request.planType() == null) {
            throw new IllegalArgumentException("planType é obrigatório");
        }
        if (request.sources() == null || request.sources().isEmpty()) {
            throw new IllegalArgumentException("sources deve conter pelo menos uma origem");
        }
        if (request.sources().stream().anyMatch(source -> source == null || source.isBlank())) {
            throw new IllegalArgumentException("sources não pode conter entradas nulas ou em branco");
        }
    }

    private List<String> normalizeSources(List<String> sources) {
        Set<String> unique = new LinkedHashSet<>();
        for (String source : sources) {
            unique.add(Path.of(source).toAbsolutePath().normalize().toString());
        }
        return new ArrayList<>(unique);
    }

    private DeviceDtos.PlanResponse toResponse(ProtectionPlan plan) {
        return new DeviceDtos.PlanResponse(plan.planType, List.copyOf(plan.sources), plan.updatedAt);
    }
}
