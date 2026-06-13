import 'dart:async';
import 'dart:convert';
import 'dart:io';
import 'package:http/http.dart' as http;
import '../models/remote_file.dart';
import 'secure_storage_service.dart';
import '../core/constants/api_endpoints.dart';
import '../core/constants/app_constants.dart';

class ApiClientService {
  static final ApiClientService _instance = ApiClientService._();
  factory ApiClientService() => _instance;
  ApiClientService._();
  final SecureStorageService _secureStorage = SecureStorageService();
  static const Duration _defaultTimeout = Duration(seconds: 15);
  static const Duration _downloadTimeout = Duration(minutes: 2);
  static const int _maxRetries = 3;
  Future<String> _getToken() async {
    try {
      final token = await _secureStorage.getToken();
      return token ?? '';
    } catch (_) {
      return '';
    }
  }

  Future<String> _getBaseUrl() async {
    try {
      final saved = await _secureStorage.getBackendUrl();
      if (saved != null && saved.isNotEmpty) return saved;
    } catch (_) {}
    return AppConstants.backendBaseUrl;
  }

  Future<Map<String, String>> _getHeaders() async {
    final token = await _getToken();
    return {
      'Content-Type': 'application/json; charset=utf-8',
      if (token.isNotEmpty) 'Authorization': 'Bearer $token',
      'User-Agent': 'KeeplyMobile/1.0',
    };
  }

  void _handleError(int statusCode, String body, String endpoint) {
    String message;
    try {
      final json = jsonDecode(body) as Map<String, dynamic>?;
      message =
          json?['message'] as String? ??
          json?['error'] as String? ??
          'Erro desconhecido';
    } catch (_) {
      message = body.isNotEmpty ? body : 'Erro desconhecido';
    }
    switch (statusCode) {
      case 401:
      case 403:
        throw TokenExpiredException(message);
      case 404:
        throw ResourceNotFoundException(
          'Recurso não encontrado em $endpoint',
          statusCode: statusCode,
        );
      case 429:
        throw RateLimitException(
          'Limite de requisições excedido. Tente novamente em alguns minutos.',
          statusCode: statusCode,
        );
      case 500:
      case 502:
      case 503:
      case 504:
        throw ServerException(
          'Servidor indisponível. Tente novamente mais tarde.',
          statusCode: statusCode,
        );
      default:
        throw ApiException(
          'Erro na requisição: $message',
          statusCode: statusCode,
        );
    }
  }

  Future<String> _getWithRetry(String url) async {
    int attempts = 0;
    while (attempts < _maxRetries) {
      try {
        final headers = await _getHeaders();
        final response = await http
            .get(Uri.parse(url), headers: headers)
            .timeout(_defaultTimeout);
        if (response.statusCode == 200) return response.body;
        if (response.statusCode == 502 || response.statusCode == 503) {
          attempts++;
          await Future.delayed(Duration(seconds: 2 * attempts));
          continue;
        }
        _handleError(response.statusCode, response.body, url);
      } on TimeoutException {
        attempts++;
        if (attempts >= _maxRetries) {
          throw ApiException(
            'Requisição expirou após $attempts tentativas',
            statusCode: 0,
          );
        }
        await Future.delayed(Duration(seconds: 2 * attempts));
        continue;
      } on SocketException {
        throw NetworkException('Sem conexão de rede. Verifique o Wi-Fi/dados.');
      }
    }
    throw ApiException(
      'Falha na requisição após $_maxRetries tentativas',
      statusCode: 0,
    );
  }

  Future<void> login(String email, String password) async {
    try {
      final baseUrl = await _getBaseUrl();
      final uri = ApiEndpoints.uri(baseUrl, ApiEndpoints.login).toString();
      final response = await http
          .post(
            Uri.parse(uri),
            headers: {
              'Content-Type': 'application/json; charset=utf-8',
              'User-Agent': 'KeeplyMobile/1.0',
            },
            body: jsonEncode({'email': email, 'password': password}),
          )
          .timeout(_defaultTimeout);
      if (response.statusCode != 200) {
        _handleError(response.statusCode, response.body, uri);
      }
      final json = jsonDecode(response.body) as Map<String, dynamic>;
      final accessToken =
          json['accessToken'] as String? ??
          json['token'] as String? ??
          json['jwtToken'] as String?;
      if (accessToken == null || accessToken.isEmpty) {
        throw ApiException(
          'Token JWT não recebido do servidor',
          statusCode: 200,
        );
      }
      await _secureStorage.saveToken(accessToken);
      final respEmail = json['email'] as String? ?? '';
      if (respEmail.isNotEmpty) {
        await _secureStorage.saveUserEmail(respEmail);
        final parts = respEmail.split('@');
        if (parts.isNotEmpty) {
          final rawName = parts[0];
          final formattedName = rawName[0].toUpperCase() + rawName.substring(1);
          await _secureStorage.saveUserName(formattedName);
        }
      }
      await _secureStorage.setPairingStatus(true);
    } on SocketException {
      throw NetworkException('Sem conexão de rede durante login.');
    } on TokenExpiredException {
      rethrow;
    } on ApiException {
      rethrow;
    } catch (e) {
      throw ApiException('Erro ao fazer login: $e', statusCode: 0);
    }
  }

  Future<List<RemoteFile>> listFiles({
    String? query,
    int page = 1,
    int pageSize = 50,
  }) async {
    try {
      final baseUrl = await _getBaseUrl();
      final springPage = page - 1;
      final uri = ApiEndpoints.uri(baseUrl, ApiEndpoints.snapshots, {
        'page': springPage,
        'size': pageSize,
      }).toString();
      final body = await _getWithRetry(uri);
      final json = jsonDecode(body) as Map<String, dynamic>;
      final items = (json['items'] as List<dynamic>?) ?? [];
      return items
          .map((e) => RemoteFile.fromSnapshotJson(e as Map<String, dynamic>))
          .toList();
    } on TokenExpiredException {
      rethrow;
    } on ApiException {
      rethrow;
    } catch (e) {
      throw ApiException('Erro ao listar snapshots: $e', statusCode: 0);
    }
  }

  Future<List<RemoteFile>> listSnapshotFiles({
    required String snapshotId,
    int page = 0,
    int size = 100,
    String? search,
  }) async {
    try {
      final baseUrl = await _getBaseUrl();
      final uri =
          ApiEndpoints.uri(baseUrl, ApiEndpoints.snapshotFiles(snapshotId), {
            'page': page,
            'size': size,
            if (search != null && search.isNotEmpty) 'search': search,
          }).toString();
      final body = await _getWithRetry(uri);
      final json = jsonDecode(body) as Map<String, dynamic>;
      final items =
          (json['items'] as List<dynamic>?) ??
          (json['files'] as List<dynamic>?) ??
          [];
      return items
          .map(
            (e) => RemoteFile.fromSnapshotFileJson(e as Map<String, dynamic>),
          )
          .toList();
    } on TokenExpiredException {
      rethrow;
    } on ApiException {
      rethrow;
    } catch (e) {
      throw ApiException(
        'Erro ao listar arquivos do snapshot: $e',
        statusCode: 0,
      );
    }
  }

  Future<void> deleteSnapshot(String snapshotId) async {
    final baseUrl = await _getBaseUrl();
    final uri = ApiEndpoints.uri(
      baseUrl,
      ApiEndpoints.snapshot(snapshotId),
    ).toString();
    final headers = await _getHeaders();
    final response = await http
        .delete(Uri.parse(uri), headers: headers)
        .timeout(_defaultTimeout);
    if (response.statusCode != 200 && response.statusCode != 204) {
      _handleError(response.statusCode, response.body, uri);
    }
  }

  Future<File> downloadFile(
    String snapshotId,
    String filePath,
    String destinationPath,
  ) async {
    try {
      final baseUrl = await _getBaseUrl();
      final uri = ApiEndpoints.uri(
        baseUrl,
        '${ApiEndpoints.snapshotFiles(snapshotId)}/download',
        {'path': filePath},
      ).toString();
      final headers = await _getHeaders();
      final response = await http
          .get(Uri.parse(uri), headers: headers)
          .timeout(_downloadTimeout);
      if (response.statusCode != 200) {
        _handleError(response.statusCode, response.body, uri);
      }
      final file = File(destinationPath);
      await file.parent.create(recursive: true);
      await file.writeAsBytes(response.bodyBytes);
      return file;
    } on SocketException {
      throw NetworkException('Sem conexão de rede durante download.');
    } on TokenExpiredException {
      rethrow;
    } on ApiException {
      rethrow;
    } catch (e) {
      throw ApiException('Erro ao baixar arquivo: $e', statusCode: 0);
    }
  }

  Future<String> exchangeQrToken({
    required String qrToken,
    required String host,
  }) async {
    try {
      var baseUrl = host;
      if (!baseUrl.startsWith('http://') && !baseUrl.startsWith('https://')) {
        final isLocal =
            baseUrl.contains('127.0.0.1') ||
            baseUrl.contains('localhost') ||
            baseUrl.contains('192.168.') ||
            baseUrl.contains('10.') ||
            baseUrl.contains(':8080');
        baseUrl = '${isLocal ? 'http://' : 'https://'}$baseUrl';
      }
      final uri = ApiEndpoints.uri(baseUrl, '/api/auth/qr/exchange').toString();
      final headers = {
        'Content-Type': 'application/json; charset=utf-8',
        'User-Agent': 'KeeplyMobile/1.0',
      };
      final response = await http
          .post(
            Uri.parse(uri),
            headers: headers,
            body: jsonEncode({'token': qrToken}),
          )
          .timeout(_defaultTimeout);
      if (response.statusCode != 200 && response.statusCode != 201) {
        _handleError(response.statusCode, response.body, uri);
      }
      final json = jsonDecode(response.body) as Map<String, dynamic>;
      final jwtToken =
          json['accessToken'] as String? ?? json['token'] as String?;
      if (jwtToken == null || jwtToken.isEmpty) {
        throw ApiException(
          'Token JWT não recebido do servidor',
          statusCode: 200,
        );
      }
      await _secureStorage.saveBackendUrl(baseUrl);
      return jwtToken;
    } on SocketException {
      throw NetworkException('Sem conexão de rede durante pareamento.');
    } on TokenExpiredException {
      rethrow;
    } on ApiException {
      rethrow;
    } catch (e) {
      throw ApiException('Erro ao trocar token QR: $e', statusCode: 0);
    }
  }

  Future<String> registerDevice({
    required String name,
    required String hostname,
    required String osName,
    required String agentVersion,
  }) async {
    try {
      final baseUrl = await _getBaseUrl();
      final uri = ApiEndpoints.uri(
        baseUrl,
        ApiEndpoints.registerDevice,
      ).toString();
      final token = await _getToken();
      final response = await http
          .post(
            Uri.parse(uri),
            headers: {
              'Content-Type': 'application/json; charset=utf-8',
              'User-Agent': 'KeeplyMobile/1.0',
              'Authorization': 'Bearer $token',
            },
            body: jsonEncode({
              'name': name,
              'hostname': hostname,
              'osName': osName,
              'agentVersion': agentVersion,
            }),
          )
          .timeout(_defaultTimeout);
      if (response.statusCode != 200 && response.statusCode != 201) {
        _handleError(response.statusCode, response.body, uri);
      }
      final json = jsonDecode(response.body) as Map<String, dynamic>;
      final deviceId = json['id'] as String?;
      if (deviceId == null || deviceId.isEmpty) {
        throw ApiException(
          'ID do dispositivo não recebido do servidor',
          statusCode: response.statusCode,
        );
      }
      await _secureStorage.saveDeviceId(deviceId);
      return deviceId;
    } on SocketException {
      throw NetworkException(
        'Sem conexão de rede durante registro de dispositivo.',
      );
    } on TokenExpiredException {
      rethrow;
    } on ApiException {
      rethrow;
    } catch (e) {
      throw ApiException('Erro ao registrar dispositivo: $e', statusCode: 0);
    }
  }
}

class ApiException implements Exception {
  final String message;
  final int statusCode;
  ApiException(this.message, {required this.statusCode});
  @override
  String toString() => 'ApiException: $message (HTTP $statusCode)';
}

class TokenExpiredException extends ApiException {
  TokenExpiredException(String message) : super(message, statusCode: 401);
  @override
  String toString() => 'TokenExpiredException: $message';
}

class ResourceNotFoundException extends ApiException {
  ResourceNotFoundException(String message, {required int statusCode})
    : super(message, statusCode: statusCode);
  @override
  String toString() => 'ResourceNotFoundException: $message';
}

class RateLimitException extends ApiException {
  RateLimitException(String message, {required int statusCode})
    : super(message, statusCode: statusCode);
  @override
  String toString() => 'RateLimitException: $message';
}

class ServerException extends ApiException {
  ServerException(String message, {required int statusCode})
    : super(message, statusCode: statusCode);
  @override
  String toString() => 'ServerException: $message';
}

class NetworkException extends ApiException {
  NetworkException(String message) : super(message, statusCode: 0);
  @override
  String toString() => 'NetworkException: $message';
}
