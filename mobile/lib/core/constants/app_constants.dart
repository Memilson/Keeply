import 'api_endpoints.dart';

class AppConstants {
  static const String routeSplash = '/splash';
  static const String routeHome = '/home';
  static const String routeDashboard = '/dashboard';
  static const String routePairing = '/pairing';
  static const String routeScanHistory = '/scan_history';
  static const String routeFiles = '/files';
  static const String appName = 'Keeply';
  static const String appSlogan = 'Backup Inteligente para seu Dispositivo';
  static const String noBackupsMessage = 'Nenhum backup encontrado';
  static const String loadingMessage = 'Carregando...';
  static const int splashDurationMillis = 2000;
  static const int apiTimeoutSeconds = 30;
  static const int maxRetryAttempts = 3;
  static const String backendBaseUrl = ApiEndpoints.defaultBackendBaseUrl;
  static const String storageKeyBiometricsEnabled = 'keeply_biometrics_enabled';
  static const int wsReconnectMs = 5000;
  static const int wsKeepAliveMs = 25000;
  static const int wsMaxMsgsPerMinute = 60;
  static const String storageKeyDeviceId = 'keeply_device_id';
  static const String storageKeyUserId = 'keeply_user_id';
  static const String storageKeyFingerprint = 'keeply_fingerprint_sha256';
}
