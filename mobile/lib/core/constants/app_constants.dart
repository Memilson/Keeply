/// [AppConstants] - Centraliza todas as constantes do aplicativo.
class AppConstants {
  // ==================== ROTAS ====================
  static const String routeSplash = '/splash';
  static const String routeHome = '/home';
  static const String routeDashboard = '/dashboard';
  static const String routePairing = '/pairing';
  static const String routeScanHistory = '/scan_history';
  static const String routeFiles = '/files';

  // ==================== STRINGS DE UI ====================
  static const String appName = 'Keeply';
  static const String appSlogan = 'Backup Inteligente para seu Dispositivo';
  static const String noBackupsMessage = 'Nenhum backup encontrado';
  static const String loadingMessage = 'Carregando...';

  // ==================== CONFIGURAÇÕES ====================
  static const int splashDurationMillis = 2000;
  static const int apiTimeoutSeconds = 30;
  static const int maxRetryAttempts = 3;

  // ==================== WEBSOCKET ====================
  static const String wsDefaultUrl = 'wss://backend.keeply.app.br/ws/agent';
  static const String backendBaseUrl = 'https://backend.keeply.app.br';
  static const String storageKeyBiometricsEnabled = 'keeply_biometrics_enabled';
  static const int wsReconnectMs = 5000;
  static const int wsKeepAliveMs = 25000;
  static const int wsMaxMsgsPerMinute = 60;

  // ==================== CHAVES DE STORAGE ====================
  static const String storageKeyDeviceId = 'keeply_device_id';
  static const String storageKeyUserId = 'keeply_user_id';
  static const String storageKeyFingerprint = 'keeply_fingerprint_sha256';
}
