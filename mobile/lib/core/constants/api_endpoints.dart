class ApiEndpoints {
  static const String defaultBackendBaseUrl = String.fromEnvironment(
    'KEEPLY_BACKEND_BASE_URL',
    defaultValue: 'https://keeply.app.br',
  );

  static const String health = '/api/actuator/health';
  static const String register = '/api/auth/register';
  static const String login = '/api/auth/login';
  static const String loginDevice = '/api/auth/login-device';
  static const String refresh = '/api/auth/refresh';
  static const String aiChat = '/api/ai/chat';
  static const String devices = '/api/devices';
  static const String registerDevice = '/api/devices/register';
  static const String chunksCheck = '/api/chunks/check';
  static const String storageUsage = '/api/chunks/storage-usage';
  static const String snapshots = '/api/snapshots';

  static String deviceHeartbeat(String deviceId) =>
      '/api/devices/$deviceId/heartbeat';

  static String devicePlan(String deviceId) => '/api/devices/$deviceId/plan';

  static String snapshot(String snapshotId) => '/api/snapshots/$snapshotId';

  static String snapshotComplete(String snapshotId) =>
      '/api/snapshots/$snapshotId/complete';

  static String snapshotFail(String snapshotId) =>
      '/api/snapshots/$snapshotId/fail';

  static String snapshotRestoreSessions(String snapshotId) =>
      '/api/snapshots/$snapshotId/restore-sessions';

  static String snapshotFiles(String snapshotId) =>
      '/api/snapshots/$snapshotId/files';

  static String snapshotNodes(String snapshotId) =>
      '/api/snapshots/$snapshotId/nodes';

  static String snapshotArchiveSelected(String snapshotId) =>
      '/api/snapshots/$snapshotId/archive-selected';

  static String transferRenew(String id) => '/api/transfer-sessions/$id/renew';

  static String transferCancel(String id) =>
      '/api/transfer-sessions/$id/cancel';

  static String transferFinish(String id) =>
      '/api/transfer-sessions/$id/finish';

  static Uri uri(String baseUrl, String path, [Map<String, dynamic>? query]) {
    final normalizedBase = baseUrl.endsWith('/')
        ? baseUrl.substring(0, baseUrl.length - 1)
        : baseUrl;
    final uri = Uri.parse('$normalizedBase$path');
    if (query == null || query.isEmpty) return uri;
    return uri.replace(
      queryParameters: query.map((key, value) => MapEntry(key, '$value')),
    );
  }
}
