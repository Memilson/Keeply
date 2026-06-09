import 'dart:convert';
import 'dart:io';

import 'package:http/http.dart' as http;

import '../models/remote_file.dart';

/// Cliente HTTP simples para integração com o backend.
class ApiClient {
  final String baseUrl;
  final http.Client _client;

  ApiClient({required this.baseUrl, http.Client? client})
    : _client = client ?? http.Client();

  /// Lista arquivos do servidor. Retorna uma lista de `RemoteFile`.
  /// Query é usada para busca inteligente no backend.
  Future<List<RemoteFile>> listFiles({
    String? query,
    int page = 1,
    int pageSize = 50,
  }) async {
    final q = query != null && query.isNotEmpty
        ? '&q=${Uri.encodeQueryComponent(query)}'
        : '';
    final uri = Uri.parse('$baseUrl/api/files?page=$page&pageSize=$pageSize$q');
    final resp = await _client.get(uri).timeout(const Duration(seconds: 15));
    if (resp.statusCode != 200) {
      throw Exception('Falha ao listar arquivos: ${resp.statusCode}');
    }
    final json = jsonDecode(resp.body) as Map<String, dynamic>;
    final items = (json['items'] as List<dynamic>?) ?? [];
    return items
        .map((e) => RemoteFile.fromJson(e as Map<String, dynamic>))
        .toList();
  }

  /// Obtém metadados de um arquivo pelo ID.
  Future<RemoteFile> getFile(String id) async {
    final uri = Uri.parse('$baseUrl/api/files/$id');
    final resp = await _client.get(uri).timeout(const Duration(seconds: 15));
    if (resp.statusCode != 200) {
      throw Exception('Falha ao obter arquivo: ${resp.statusCode}');
    }
    final json = jsonDecode(resp.body) as Map<String, dynamic>;
    return RemoteFile.fromJson(json);
  }

  /// Faz o download do arquivo e grava em `destPath` no dispositivo.
  /// Retorna o `File` salvo.
  Future<File> downloadFileToPath(String id, String destPath) async {
    final uri = Uri.parse('$baseUrl/api/files/$id/download');
    final resp = await _client.get(uri).timeout(const Duration(minutes: 2));
    if (resp.statusCode != 200) {
      throw Exception('Falha ao baixar arquivo: ${resp.statusCode}');
    }
    final file = File(destPath);
    await file.create(recursive: true);
    await file.writeAsBytes(resp.bodyBytes);
    return file;
  }

  void dispose() => _client.close();
}
