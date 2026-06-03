package com.keeply.backend.service;

import com.keeply.backend.dto.DeviceDtos;
import com.keeply.backend.exception.ForbiddenException;
import com.keeply.backend.exception.NotFoundException;
import com.keeply.backend.model.Device;
import com.keeply.backend.model.PlanType;
import com.keeply.backend.model.ProtectionPlan;
import com.keeply.backend.model.RetentionMode;
import com.keeply.backend.repository.DeviceRepository;
import com.keeply.backend.repository.ProtectionPlanRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class ProtectionPlanService {
    private static final String DEFAULT_DAILY_CRON = "0 2 * * *";
    // VULN-011: padrão unix-cron com 5 campos (minuto hora dia-mês mês dia-semana)
    private static final Pattern UNIX_CRON_PATTERN = Pattern.compile(
            "^(\\S+\\s){4}\\S+$"
    );

    private final DeviceRepository devices;
    private final ProtectionPlanRepository plans;
    private final PasswordEncoder passwordEncoder;

    public ProtectionPlanService(DeviceRepository devices, ProtectionPlanRepository plans, PasswordEncoder passwordEncoder) {
        this.devices = devices;
        this.plans = plans;
        this.passwordEncoder = passwordEncoder;
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
        ProtectionPlan plan = plans.findByDeviceId(deviceId).orElseGet(ProtectionPlan::new);
        RetentionMode retentionMode = resolveRetentionMode(request, plan);
        validatePlanRequest(request, retentionMode);

        plan.device = device;
        plan.planType = request.planType();
        plan.sources = normalizeSources(request.sources());
        plan.cdpEnabled = Boolean.TRUE.equals(request.cdpEnabled());
        plan.encryptionEnabled = Boolean.TRUE.equals(request.encryptionEnabled());
        plan.scheduleCron = normalizeSchedule(request.scheduleCron());
        plan.retentionMode = retentionMode;
        plan.retentionDays = plan.retentionMode == RetentionMode.KEEP_DAYS
                ? request.retentionDays() != null ? request.retentionDays() : plan.retentionDays
                : null;
        if (request.encryptionPassword() != null && !request.encryptionPassword().isBlank()) {
            plan.encryptionPasswordHash = passwordEncoder.encode(request.encryptionPassword());
        } else if (!plan.encryptionEnabled) {
            plan.encryptionPasswordHash = null;
        }

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

    private void validatePlanRequest(DeviceDtos.PlanRequest request, RetentionMode retentionMode) {
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

        if (retentionMode == RetentionMode.KEEP_DAYS) {
            if (request.retentionDays() == null || request.retentionDays() <= 0) {
                throw new IllegalArgumentException("retentionDays deve ser maior que zero quando retentionMode=KEEP_DAYS");
            }
        } else if (request.retentionDays() != null) {
            throw new IllegalArgumentException("retentionDays deve ser nulo quando retentionMode=KEEP_ALL");
        }
    }

    private RetentionMode resolveRetentionMode(DeviceDtos.PlanRequest request, ProtectionPlan plan) {
        if (request.retentionMode() != null) {
            return request.retentionMode();
        }
        if (plan.retentionMode != null) {
            return plan.retentionMode;
        }
        return RetentionMode.KEEP_ALL;
    }

    private List<String> normalizeSources(List<String> sources) {
        // VULN-014: não resolver paths no servidor — armazenar como veio do cliente
        // Path.of().toAbsolutePath() resolvia em relação ao cwd do processo Java, não do cliente
        Set<String> unique = new LinkedHashSet<>();
        for (String source : sources) {
            if (source != null && !source.isBlank()) {
                unique.add(source.trim());
            }
        }
        return new ArrayList<>(unique);
    }

    private DeviceDtos.PlanResponse toResponse(ProtectionPlan plan) {
        return new DeviceDtos.PlanResponse(
                plan.planType, List.copyOf(plan.sources),
                plan.cdpEnabled, plan.encryptionEnabled, normalizeSchedule(plan.scheduleCron),
                plan.retentionMode, plan.retentionDays,
                plan.encryptionPasswordHash != null && !plan.encryptionPasswordHash.isBlank(),
                plan.updatedAt);
    }

    private String normalizeSchedule(String scheduleCron) {
        if (scheduleCron == null || scheduleCron.isBlank()) {
            return DEFAULT_DAILY_CRON;
        }
        String trimmed = scheduleCron.trim();
        // VULN-011: validar formato unix-cron antes de persistir
        if (!UNIX_CRON_PATTERN.matcher(trimmed).matches()) {
            throw new IllegalArgumentException(
                    "scheduleCron inválido: esperado 5 campos unix-cron (ex: '0 2 * * *'), recebido: '" + trimmed + "'"
            );
        }
        return trimmed;
    }
}
