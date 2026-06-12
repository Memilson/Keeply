package com.keeply.agent.api;

import java.util.UUID;

public final class ApiEndpoints {
    public static final String DEFAULT_BASE_URL = firstNonBlank(
            System.getProperty("keeply.backend.url"),
            System.getenv("KEEPLY_BACKEND_URL"),
            "https://keeply.app.br");

    public static final String HEALTH = "/api/actuator/health";
    public static final String AUTH_REGISTER = "/api/auth/register";
    public static final String AUTH_LOGIN = "/api/auth/login";
    public static final String AUTH_LOGIN_DEVICE = "/api/auth/login-device";
    public static final String AUTH_REFRESH = "/api/auth/refresh";
    public static final String AUTH_QR = "/api/auth/qr";
    public static final String AI_CHAT = "/api/ai/chat";
    public static final String DEVICES = "/api/devices";
    public static final String DEVICE_REGISTER = "/api/devices/register";
    public static final String CHUNKS_CHECK = "/api/chunks/check";
    public static final String STORAGE_USAGE = "/api/chunks/storage-usage";
    public static final String SNAPSHOTS = "/api/snapshots";
    public static final String SNAPSHOT_START = "/api/snapshots/start";

    private ApiEndpoints() {
    }

    public static String deviceHeartbeat(UUID deviceId) {
        return "/api/devices/" + deviceId + "/heartbeat";
    }

    public static String devicePlan(UUID deviceId) {
        return "/api/devices/" + deviceId + "/plan";
    }

    public static String snapshot(UUID snapshotId) {
        return "/api/snapshots/" + snapshotId;
    }

    public static String snapshotComplete(UUID snapshotId) {
        return snapshot(snapshotId) + "/complete";
    }

    public static String snapshotFail(UUID snapshotId) {
        return snapshot(snapshotId) + "/fail";
    }

    public static String snapshotRestoreSessions(UUID snapshotId) {
        return snapshot(snapshotId) + "/restore-sessions";
    }

    public static String snapshotFiles(UUID snapshotId) {
        return snapshot(snapshotId) + "/files";
    }

    public static String snapshotNodes(UUID snapshotId) {
        return snapshot(snapshotId) + "/nodes";
    }

    public static String snapshotArchiveSelected(UUID snapshotId) {
        return snapshot(snapshotId) + "/archive-selected";
    }

    public static String transferRenew(UUID transferSessionId) {
        return "/api/transfer-sessions/" + transferSessionId + "/renew";
    }

    public static String transferCancel(UUID transferSessionId) {
        return "/api/transfer-sessions/" + transferSessionId + "/cancel";
    }

    public static String transferFinish(UUID transferSessionId) {
        return "/api/transfer-sessions/" + transferSessionId + "/finish";
    }

    public static String normalizeBaseUrl(String baseUrl) {
        String value = firstNonBlank(baseUrl, DEFAULT_BASE_URL);
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        throw new IllegalArgumentException("Nenhuma URL base configurada");
    }
}
