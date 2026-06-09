import 'package:flutter_secure_storage/flutter_secure_storage.dart';

/// [SecureStorageService] - Gerenciador de armazenamento seguro e criptografado.
///
/// Responsabilidades:
/// - Armazenar JWT tokens de forma segura e criptografada (não em SharedPreferences)
/// - Persistir URL do backend e credenciais de pareamento
/// - Gerenciar remoção segura de dados sensíveis durante logout
///
/// Segurança:
/// - iOS: Keychain
/// - Android: EncryptedSharedPreferences ou Keystore
/// - Nunca expõe dados sensíveis em logs ou debugging
///
/// Uso:
/// ```dart
/// final storage = SecureStorageService();
/// await storage.saveToken('eyJhbGc...');
/// final token = await storage.getToken();
/// await storage.clear();
/// ```
class SecureStorageService {
  static final SecureStorageService _instance = SecureStorageService._();
  factory SecureStorageService() => _instance;
  SecureStorageService._();

  /// Instância de armazenamento seguro do Flutter.
  /// Utiliza Keychain (iOS) ou Keystore (Android) nativamente.
  static const _storage = FlutterSecureStorage(
    aOptions: _androidOptions,
    iOptions: _iOSOptions,
  );

  /// Opções de segurança para Android.
  /// - encryptedSharedPreferences: true → força criptografia via EncryptedSharedPreferences
  static const _androidOptions = AndroidOptions(
    encryptedSharedPreferences: true,
    resetOnError: true,
  );

  /// Opções de segurança para iOS.
  /// - accessibility: first_this_device_only → token só acessível neste dispositivo
  static const _iOSOptions = IOSOptions(
    accessibility: KeychainAccessibility.first_this_device_only,
  );

  /// Chaves de armazenamento (constantes).
  /// Estas são as chaves utilizadas para salvar dados no storage seguro.
  static const String _keyJwtToken = 'keeply_jwt_token';
  static const String _keyBackendUrl = 'keeply_backend_url';
  static const String _keyDeviceId = 'keeply_device_id';
  static const String _keyPairingStatus = 'keeply_pairing_status';

  /// Salva o JWT token de forma segura e criptografada.
  ///
  /// Parâmetros:
  /// - [token]: JWT token retornado pelo backend após autenticação
  ///
  /// Lança exceção se houver erro de I/O ou permissões insuficientes.
  Future<void> saveToken(String token) async {
    try {
      await _storage.write(key: _keyJwtToken, value: token);
    } catch (e) {
      throw Exception('Erro ao salvar token seguro: $e');
    }
  }

  /// Recupera o JWT token armazenado.
  ///
  /// Retorna:
  /// - String: token JWT válido
  /// - null: se nenhum token foi salvo ou foi apagado
  ///
  /// Uso típico:
  /// ```dart
  /// final token = await SecureStorageService().getToken();
  /// if (token != null) {
  ///   headers['Authorization'] = 'Bearer $token';
  /// }
  /// ```
  Future<String?> getToken() async {
    try {
      return await _storage.read(key: _keyJwtToken);
    } catch (e) {
      throw Exception('Erro ao recuperar token: $e');
    }
  }

  /// Salva a URL do backend de forma segura.
  ///
  /// Parâmetros:
  /// - [url]: URL base do backend (ex: http://localhost:8080)
  ///
  /// Importante: Esta URL é sensível e não deve ser hardcoded.
  /// Deve ser configurada via QR code de pareamento.
  Future<void> saveBackendUrl(String url) async {
    try {
      await _storage.write(key: _keyBackendUrl, value: url);
    } catch (e) {
      throw Exception('Erro ao salvar URL backend: $e');
    }
  }

  /// Recupera a URL do backend armazenada.
  ///
  /// Retorna:
  /// - String: URL do backend
  /// - null: se não foi configurada (dispositivo não pareado)
  Future<String?> getBackendUrl() async {
    try {
      return await _storage.read(key: _keyBackendUrl);
    } catch (e) {
      throw Exception('Erro ao recuperar URL backend: $e');
    }
  }

  /// Salva o ID único do dispositivo.
  ///
  /// Parâmetros:
  /// - [deviceId]: UUID ou ID único do dispositivo (gerado uma vez durante pareamento)
  ///
  /// Este ID é usado para identificar o dispositivo nas chamadas de API.
  Future<void> saveDeviceId(String deviceId) async {
    try {
      await _storage.write(key: _keyDeviceId, value: deviceId);
    } catch (e) {
      throw Exception('Erro ao salvar device ID: $e');
    }
  }

  /// Recupera o ID do dispositivo.
  ///
  /// Retorna:
  /// - String: Device ID salvo
  /// - null: se não foi salvo (dispositivo não pareado)
  Future<String?> getDeviceId() async {
    try {
      return await _storage.read(key: _keyDeviceId);
    } catch (e) {
      throw Exception('Erro ao recuperar device ID: $e');
    }
  }

  /// Marca o status de pareamento do dispositivo.
  ///
  /// Parâmetros:
  /// - [isPaired]: true se o dispositivo foi pareado com sucesso
  ///
  /// Este flag é usado para determinar se deve exibir fluxo de pareamento.
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

  /// Recupera o status de pareamento.
  ///
  /// Retorna:
  /// - true: dispositivo está pareado
  /// - false: dispositivo não está pareado
  Future<bool> isPaired() async {
    try {
      final status = await _storage.read(key: _keyPairingStatus);
      return status == 'true';
    } catch (e) {
      throw Exception('Erro ao recuperar status de pareamento: $e');
    }
  }

  /// Limpa todos os dados sensíveis armazenados de forma segura.
  ///
  /// Uso:
  /// - Durante logout
  /// - Durante limpeza de dados do usuário
  /// - Durante reset de pareamento
  ///
  /// Após esta operação, o dispositivo será considerado não pareado
  /// e exigirá novo pareamento.
  Future<void> clearAll() async {
    try {
      await _storage.deleteAll();
    } catch (e) {
      throw Exception('Erro ao limpar armazenamento seguro: $e');
    }
  }

  /// Verifica se o dispositivo está completamente configurado.
  ///
  /// Retorna true apenas se:
  /// - Token JWT está salvo
  /// - URL do backend está salva
  /// - Device ID está salvo
  /// - Status de pareamento é true
  ///
  /// Uso típico na SplashView para determinar próxima tela.
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
}
