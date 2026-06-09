import 'dart:convert';
import 'dart:io';

import 'package:http/http.dart' as http;

import '../models/remote_file.dart';
import 'secure_storage_service.dart';

/// [ApiClientService] - Cliente HTTP com autenticação JWT e tratamento robusto.
///
/// Responsabilidades:
/// - Fazer requisições autenticadas com JWT para o backend
/// - Adicionar headers de autenticação automaticamente
/// - Tratar erros HTTP e transformá-los em exceções descritivas
/// - Fazer retry automático para erros temporários (503, timeout)
/// - Serializar/desserializar responses do backend
///
/// Integração com Backend (conforme README.md):
/// - Base URL: Lida do SecureStorageService (configurado via QR code)
/// - Autenticação: Bearer token JWT
/// - APIs: /api/files (lista), /api/files/{id} (details), /api/files/{id}/download
///
/// Fluxo típico:
/// ```
/// 1. Usuário faz login/pareamento → JWT salvo em SecureStorageService
/// 2. ApiClientService lê JWT automaticamente
/// 3. Cada request adiciona: Authorization: Bearer <token>
/// 4. Se token expirou: 401 → indicar logout/re-login
/// 5. Se erro: lançar exceção com mensagem amigável
/// ```
///
/// Uso:
/// ```dart
/// final apiClient = ApiClientService();
/// try {
///   final files = await apiClient.listFiles();
///   // sucesso
/// } on TokenExpiredException {
///   // fazer re-login
/// } on ApiException catch (e) {
///   // mostrar erro: e.message
/// }
/// ```
class ApiClientService {
  static final ApiClientService _instance = ApiClientService._();
  factory ApiClientService() => _instance;
  ApiClientService._();

  /// Instância HTTP cliente do Flutter.
  final http.Client _client = http.Client();

  /// Serviço de armazenamento seguro para acessar token e URL base.
  final SecureStorageService _secureStorage = SecureStorageService();

  /// Timeout padrão para requisições (15 segundos).
  static const Duration _defaultTimeout = Duration(seconds: 15);

  /// Timeout para download de arquivos (2 minutos).
  static const Duration _downloadTimeout = Duration(minutes: 2);

  /// Número máximo de retries para erros temporários.
  static const int _maxRetries = 3;

  // ============= HELPERS PRIVADOS =============

  /// Lê o token JWT do armazenamento seguro.
  ///
  /// Lança [TokenExpiredException] se token não encontrado.
  Future<String> _getToken() async {
    try {
      final token = await _secureStorage.getToken();
      if (token == null || token.isEmpty) {
        throw TokenExpiredException(
          'Token não encontrado. Faça login novamente.',
        );
      }
      return token;
    } catch (e) {
      throw TokenExpiredException('Erro ao recuperar token: $e');
    }
  }

  /// Lê a URL base do backend do armazenamento seguro.
  ///
  /// Lança [ApiException] se URL não está configurada (dispositivo não pareado).
  Future<String> _getBaseUrl() async {
    try {
      final url = await _secureStorage.getBackendUrl();
      if (url == null || url.isEmpty) {
        throw ApiException(
          'Backend não configurado. Parear dispositivo novamente.',
          statusCode: 0,
        );
      }
      return url;
    } catch (e) {
      throw ApiException('Erro ao recuperar URL do backend: $e', statusCode: 0);
    }
  }

  /// Constrói headers padrão com JWT.
  ///
  /// Retorna:
  /// ```json
  /// {
  ///   "Content-Type": "application/json",
  ///   "Authorization": "Bearer <token>"
  /// }
  /// ```
  Future<Map<String, String>> _getHeaders() async {
    final token = await _getToken();
    return {
      'Content-Type': 'application/json; charset=utf-8',
      'Authorization': 'Bearer $token',
      'User-Agent': 'KeeplyMobile/1.0',
    };
  }

  /// Trata erro HTTP genérico e lança exceção apropriada.
  ///
  /// Parâmetros:
  /// - [statusCode]: Código HTTP de erro
  /// - [body]: Corpo da response
  /// - [endpoint]: Endpoint acessado (para log/contexto)
  ///
  /// Comportamento:
  /// - 401/403: Erro de autenticação/permissão
  /// - 404: Recurso não encontrado
  /// - 429: Rate limit exceeded
  /// - 500-599: Erro do servidor
  /// - Outras: Erro genérico
  void _handleError(int statusCode, String body, String endpoint) {
    String message;

    try {
      final json = jsonDecode(body) as Map<String, dynamic>?;
      message = json?['message'] ?? json?['error'] ?? 'Erro desconhecido';
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

  /// Realiza requisição GET com retry automático.
  ///
  /// Retorna automaticamente após sucesso.
  /// Retenta em caso de timeout ou erro 503.
  /// Lança exceção se todos os retries falharem.
  Future<String> _getWithRetry(String url) async {
    int attempts = 0;

    while (attempts < _maxRetries) {
      try {
        final headers = await _getHeaders();
        final response = await _client
            .get(Uri.parse(url), headers: headers)
            .timeout(_defaultTimeout);

        if (response.statusCode == 200) {
          return response.body;
        }

        // Retry em caso de erro temporário
        if (response.statusCode == 503 || response.statusCode == 502) {
          attempts++;
          print('Erro temporário no servidor. Retry $attempts/$_maxRetries...');
          await Future.delayed(
            Duration(seconds: 2 * attempts),
          ); // backoff exponencial
          continue;
        }

        // Erro permanente
        _handleError(response.statusCode, response.body, url);
      } on TimeoutException {
        attempts++;
        print('Timeout. Retry $attempts/$_maxRetries...');
        if (attempts >= _maxRetries) {
          throw ApiException(
            'Requisição expirou após $attempts tentativas',
            statusCode: 0,
          );
        }
        await Future.delayed(Duration(seconds: 2 * attempts));
        continue;
      } on SocketException {
        throw NetworkException('Sem conexão de rede.');
      }
    }

    throw ApiException(
      'Falha na requisição após $_maxRetries tentativas',
      statusCode: 0,
    );
  }

  // ============= OPERAÇÕES PÚBLICAS =============

  /// Lista arquivos do backend com suporte a paginação e busca.
  ///
  /// Parâmetros:
  /// - [query]: Texto para busca (opcional)
  /// - [page]: Número da página (começando em 1)
  /// - [pageSize]: Quantidade de itens por página
  ///
  /// Retorna:
  /// - List<RemoteFile>: Lista de arquivos encontrados
  ///
  /// API:
  /// - GET /api/files?page=1&pageSize=50&q=search
  /// - Resposta: { "items": [...], "total": 100 }
  ///
  /// Uso:
  /// ```dart
  /// final files = await apiClient.listFiles(query: 'documento', page: 1);
  /// ```
  Future<List<RemoteFile>> listFiles({
    String? query,
    int page = 1,
    int pageSize = 50,
  }) async {
    try {
      final baseUrl = await _getBaseUrl();
      final q = query != null && query.isNotEmpty
          ? '&q=${Uri.encodeQueryComponent(query)}'
          : '';
      final uri = '$baseUrl/api/files?page=$page&pageSize=$pageSize$q';

      final body = await _getWithRetry(uri);
      final json = jsonDecode(body) as Map<String, dynamic>;
      final items = (json['items'] as List<dynamic>?) ?? [];

      return items
          .map((e) => RemoteFile.fromJson(e as Map<String, dynamic>))
          .toList();
    } on TokenExpiredException {
      rethrow;
    } on ApiException {
      rethrow;
    } catch (e) {
      throw ApiException('Erro ao listar arquivos: $e', statusCode: 0);
    }
  }

  /// Obtém metadados de um arquivo específico.
  ///
  /// Parâmetros:
  /// - [fileId]: ID único do arquivo
  ///
  /// Retorna:
  /// - RemoteFile: Metadados completos do arquivo
  ///
  /// API:
  /// - GET /api/files/{id}
  ///
  /// Lança:
  /// - [ResourceNotFoundException] se arquivo não existe
  /// - [TokenExpiredException] se token expirou
  ///
  /// Uso:
  /// ```dart
  /// final file = await apiClient.getFile('file-id-123');
  /// print('${file.name} - ${file.sizeBytes} bytes');
  /// ```
  Future<RemoteFile> getFile(String fileId) async {
    try {
      final baseUrl = await _getBaseUrl();
      final uri = '$baseUrl/api/files/$fileId';

      final body = await _getWithRetry(uri);
      final json = jsonDecode(body) as Map<String, dynamic>;

      return RemoteFile.fromJson(json);
    } on TokenExpiredException {
      rethrow;
    } on ApiException {
      rethrow;
    } catch (e) {
      throw ApiException('Erro ao obter arquivo: $e', statusCode: 0);
    }
  }

  /// Faz download de um arquivo e salva localmente.
  ///
  /// Parâmetros:
  /// - [fileId]: ID do arquivo para download
  /// - [destinationPath]: Caminho completo onde salvar o arquivo
  ///
  /// Retorna:
  /// - File: Arquivo salvo no caminho especificado
  ///
  /// API:
  /// - GET /api/files/{id}/download
  /// - Retorna o arquivo binário como stream
  ///
  /// Comportamento:
  /// - Cria diretórios se não existirem
  /// - Sobrescreve arquivo se já existe
  /// - Timeout estendido (2 minutos)
  /// - Não faz retry automático (evita múltiplos downloads)
  ///
  /// Lança:
  /// - [ApiException] se download falhar
  /// - [SocketException] se sem conexão
  ///
  /// Uso:
  /// ```dart
  /// try {
  ///   final file = await apiClient.downloadFile(
  ///     'file-id-123',
  ///     '/Downloads/documento.pdf',
  ///   );
  ///   print('Arquivo salvo em: ${file.path}');
  /// } on ApiException catch (e) {
  ///   print('Erro no download: ${e.message}');
  /// }
  /// ```
  Future<File> downloadFile(String fileId, String destinationPath) async {
    try {
      final baseUrl = await _getBaseUrl();
      final uri = '$baseUrl/api/files/$fileId/download';
      final headers = await _getHeaders();

      // Download com timeout estendido
      final response = await _client
          .get(Uri.parse(uri), headers: headers)
          .timeout(_downloadTimeout);

      if (response.statusCode != 200) {
        _handleError(response.statusCode, response.body, uri);
      }

      // Salva arquivo localmente
      final file = File(destinationPath);
      await file.parent.create(recursive: true);
      await file.writeAsBytes(response.bodyBytes);

      print(
        'Arquivo salvo: $destinationPath (${response.contentLength} bytes)',
      );
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

  /// Registra um dispositivo no backend durante o pareamento.
  ///
  /// Parâmetros:
  /// - [deviceId]: UUID único do dispositivo (gerado no mobile)
  /// - [deviceName]: Nome amigável do dispositivo (ex: "iPhone 14")
  /// - [pairingCode]: Código de pareamento do QR
  ///
  /// Retorna:
  /// - String: JWT token para autenticação futura
  ///
  /// API:
  /// - POST /api/devices/register
  /// - Headers: { 'Content-Type': 'application/json' }
  /// - Body: { "deviceId": "...", "deviceName": "...", "pairingCode": "..." }
  /// - Response: { "jwtToken": "eyJ0eXAiOiJKV1QiLC..." }
  ///
  /// Fluxo:
  /// 1. QrPairingView escaneia QR e obtém pairingCode
  /// 2. Gera UUID local (deviceId) via uuid package
  /// 3. Chama registerDevice(deviceId, deviceName, pairingCode)
  /// 4. Backend valida pairingCode + registra dispositivo
  /// 5. Retorna JWT token
  /// 6. QrPairingView salva token em SecureStorageService
  /// 7. Redireciona para SplashView → autenticação
  ///
  /// Erros Possíveis:
  /// - 400: pairingCode inválido/expirado
  /// - 409: deviceId já registrado
  /// - 500: erro no servidor
  ///
  /// Lança:
  /// - [ApiException] para erros de HTTP/rede
  /// - [NetworkException] para perda de conexão
  ///
  /// Uso:
  /// ```dart
  /// try {
  ///   final token = await apiClient.registerDevice(
  ///     deviceId: '550e8400-e29b-41d4-a716-446655440000',
  ///     deviceName: 'iPhone 14',
  ///     pairingCode: 'PAIR-ABC123-XYZ789',
  ///   );
  ///   print('Token recebido: $token');
  /// } on ApiException catch (e) {
  ///   print('Erro de pareamento: ${e.message}');
  /// }
  /// ```
  Future<String> registerDevice({
    required String deviceId,
    required String deviceName,
    required String pairingCode,
  }) async {
    try {
      final baseUrl = await _getBaseUrl();
      final uri = '$baseUrl/api/devices/register';
      final headers = await _getHeaders();
      headers['Content-Type'] = 'application/json';

      // Body da requisição
      final body = jsonEncode({
        'deviceId': deviceId,
        'deviceName': deviceName,
        'pairingCode': pairingCode,
      });

      print('Registrando dispositivo no backend: $uri');
      print('Device ID: $deviceId, Name: $deviceName');

      final response = await _client
          .post(Uri.parse(uri), headers: headers, body: body)
          .timeout(_defaultTimeout);

      if (response.statusCode != 200 && response.statusCode != 201) {
        _handleError(response.statusCode, response.body, uri);
      }

      // Parse response
      final json = jsonDecode(response.body) as Map<String, dynamic>;
      final jwtToken =
          json['jwtToken'] as String? ??
          json['token'] as String? ??
          json['accessToken'] as String?;

      if (jwtToken == null || jwtToken.isEmpty) {
        throw ApiException(
          'Token JWT não recebido do servidor',
          statusCode: 200,
        );
      }

      print('Dispositivo registrado com sucesso. Token recebido.');
      return jwtToken;
    } on SocketException {
      throw NetworkException('Sem conexão de rede durante pareamento.');
    } on TokenExpiredException {
      rethrow;
    } on ApiException {
      rethrow;
    } catch (e) {
      throw ApiException('Erro ao registrar dispositivo: $e', statusCode: 0);
    }
  }

  /// Limpa recursos do cliente HTTP.
  ///
  /// Chame antes de descartar a instância.
  void dispose() {
    _client.close();
  }
}

// ============= EXCEÇÕES CUSTOMIZADAS =============

/// [ApiException] - Exceção base para erros de API.
class ApiException implements Exception {
  final String message;
  final int statusCode;

  ApiException(this.message, {required this.statusCode});

  @override
  String toString() => 'ApiException: $message (HTTP $statusCode)';
}

/// [TokenExpiredException] - Token JWT expirou ou inválido.
/// Indica necessidade de logout/re-login.
class TokenExpiredException extends ApiException {
  TokenExpiredException(String message) : super(message, statusCode: 401);

  @override
  String toString() => 'TokenExpiredException: $message';
}

/// [ResourceNotFoundException] - Recurso não encontrado (404).
class ResourceNotFoundException extends ApiException {
  ResourceNotFoundException(String message, {required int statusCode})
    : super(message, statusCode: statusCode);

  @override
  String toString() => 'ResourceNotFoundException: $message';
}

/// [RateLimitException] - Rate limit excedido (429).
class RateLimitException extends ApiException {
  RateLimitException(String message, {required int statusCode})
    : super(message, statusCode: statusCode);

  @override
  String toString() => 'RateLimitException: $message';
}

/// [ServerException] - Erro no servidor (5xx).
class ServerException extends ApiException {
  ServerException(String message, {required int statusCode})
    : super(message, statusCode: statusCode);

  @override
  String toString() => 'ServerException: $message';
}

/// [NetworkException] - Erro de rede/conectividade.
class NetworkException extends ApiException {
  NetworkException(String message) : super(message, statusCode: 0);

  @override
  String toString() => 'NetworkException: $message';
}
