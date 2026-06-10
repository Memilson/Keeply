import 'package:flutter_secure_storage/flutter_secure_storage.dart';
class SecureStorageService {
  static final SecureStorageService _instance = SecureStorageService._();
  factory SecureStorageService() => _instance;
  SecureStorageService._();
  static const _storage = FlutterSecureStorage(aOptions: _androidOptions);
  static const _androidOptions = AndroidOptions(
    encryptedSharedPreferences: true,
    resetOnError: true,
  );
  static const String _keyJwtToken = 'keeply_jwt_token';
  static const String _keyBackendUrl = 'keeply_backend_url';
  static const String _keyDeviceId = 'keeply_device_id';
  static const String _keyPairingStatus = 'keeply_pairing_status';
  static const String _keyUserEmail = 'keeply_user_email';
  static const String _keyUserName = 'keeply_user_name';
  static const String _keyBiometricsEnabled = 'keeply_biometrics_enabled';
  static const String _keyDownloadDir = 'keeply_download_dir';
  Future<void> saveToken(String token) async {
    try {
      await _storage.write(key: _keyJwtToken, value: token);
    } catch (e) {
      throw Exception('Erro ao salvar token seguro: $e');
    }
  }
  Future<String?> getToken() async {
    try {
      return await _storage.read(key: _keyJwtToken);
    } catch (e) {
      throw Exception('Erro ao recuperar token: $e');
    }
  }
  Future<void> saveBackendUrl(String url) async {
    try {
      await _storage.write(key: _keyBackendUrl, value: url);
    } catch (e) {
      throw Exception('Erro ao salvar URL backend: $e');
    }
  }
  Future<String?> getBackendUrl() async {
    try {
      return await _storage.read(key: _keyBackendUrl);
    } catch (e) {
      throw Exception('Erro ao recuperar URL backend: $e');
    }
  }
  Future<void> saveDeviceId(String deviceId) async {
    try {
      await _storage.write(key: _keyDeviceId, value: deviceId);
    } catch (e) {
      throw Exception('Erro ao salvar device ID: $e');
    }
  }
  Future<String?> getDeviceId() async {
    try {
      return await _storage.read(key: _keyDeviceId);
    } catch (e) {
      throw Exception('Erro ao recuperar device ID: $e');
    }
  }
  Future<void> setPairingStatus(bool isPaired) async {
    try {
      await _storage.write(
        key: _keyPairingStatus,
        value: isPaired ? 'true' : 'false',
      );
    } catch (e) {
      throw Exception('Erro ao salvar status de pareamento: $e');
    }
  }
  Future<bool> isPaired() async {
    try {
      final status = await _storage.read(key: _keyPairingStatus);
      return status == 'true';
    } catch (e) {
      throw Exception('Erro ao recuperar status de pareamento: $e');
    }
  }
  Future<void> clearAll() async {
    try {
      await _storage.deleteAll();
    } catch (e) {
      throw Exception('Erro ao limpar armazenamento seguro: $e');
    }
  }
  Future<bool> isFullyConfigured() async {
    try {
      final token = await getToken();
      final url = await getBackendUrl();
      final deviceId = await getDeviceId();
      final paired = await isPaired();
      return token != null &&
          token.isNotEmpty &&
          url != null &&
          url.isNotEmpty &&
          deviceId != null &&
          deviceId.isNotEmpty &&
          paired;
    } catch (e) {
      return false;
    }
  }
  Future<void> saveUserEmail(String email) async {
    try {
      await _storage.write(key: _keyUserEmail, value: email);
    } catch (e) {
      throw Exception('Erro ao salvar email do usuário: $e');
    }
  }
  Future<String?> getUserEmail() async {
    try {
      return await _storage.read(key: _keyUserEmail);
    } catch (e) {
      throw Exception('Erro ao recuperar email do usuário: $e');
    }
  }
  Future<void> saveUserName(String name) async {
    try {
      await _storage.write(key: _keyUserName, value: name);
    } catch (e) {
      throw Exception('Erro ao salvar nome do usuário: $e');
    }
  }
  Future<String?> getUserName() async {
    try {
      return await _storage.read(key: _keyUserName);
    } catch (e) {
      throw Exception('Erro ao recuperar nome do usuário: $e');
    }
  }
  Future<void> setBiometricsEnabled(bool enabled) async {
    try {
      await _storage.write(
        key: _keyBiometricsEnabled,
        value: enabled ? 'true' : 'false',
      );
    } catch (e) {
      throw Exception('Erro ao salvar status de biometria: $e');
    }
  }
  Future<bool> isBiometricsEnabled() async {
    try {
      final val = await _storage.read(key: _keyBiometricsEnabled);
      return val == 'true';
    } catch (e) {
      return false;
    }
  }
  Future<void> saveDownloadDir(String path) async {
    try {
      await _storage.write(key: _keyDownloadDir, value: path);
    } catch (e) {
      throw Exception('Erro ao salvar pasta de download: $e');
    }
  }
  Future<String?> getDownloadDir() async {
    try {
      return await _storage.read(key: _keyDownloadDir);
    } catch (e) {
      return null;
    }
  }
}
