import 'dart:convert';
import 'dart:io';
import 'package:http/http.dart' as http;
import '../models/remote_file.dart';
class ApiClient {
  final String baseUrl;
  final String? token;
  ApiClient({required this.baseUrl, this.token});
  Map<String, String> get _headers => {
    'Content-Type': 'application/json; charset=utf-8',
    if (token != null && token!.isNotEmpty) 'Authorization': 'Bearer $token',
  };
  Future<List<RemoteFile>> listFiles({
    String? query,
    int page = 1,
    int pageSize = 50,
  }) async {
    final q = query != null && query.isNotEmpty
        ? '&q=${Uri.encodeQueryComponent(query)}'
        : '';
    final uri = Uri.parse('$baseUrl/api/snapshots?page=${page - 1}&size=$pageSize$q');
    final resp = await http.get(uri, headers: _headers).timeout(const Duration(seconds: 15));
    if (resp.statusCode != 200) {
      throw Exception('Falha ao listar snapshots: ${resp.statusCode}');
    }
    final json = jsonDecode(resp.body) as Map<String, dynamic>;
    final items = (json['snapshots'] as List<dynamic>?) ?? [];
    return items
        .map((e) => RemoteFile.fromSnapshotJson(e as Map<String, dynamic>))
        .toList();
  }
  Future<RemoteFile> getFile(String id) async {
    final uri = Uri.parse('$baseUrl/api/snapshots/$id');
    final resp = await http.get(uri, headers: _headers).timeout(const Duration(seconds: 15));
    if (resp.statusCode != 200) {
      throw Exception('Falha ao obter snapshot: ${resp.statusCode}');
    }
    final json = jsonDecode(resp.body) as Map<String, dynamic>;
    return RemoteFile.fromSnapshotJson(json);
  }
  Future<File> downloadFileToPath(String snapshotId, String filePath, String destPath) async {
    final encodedPath = Uri.encodeQueryComponent(filePath);
    final uri = Uri.parse('$baseUrl/api/snapshots/$snapshotId/files/download?path=$encodedPath');
    final resp = await http.get(uri, headers: _headers).timeout(const Duration(minutes: 2));
    if (resp.statusCode != 200) {
      throw Exception('Falha ao baixar arquivo: ${resp.statusCode}');
    }
    final file = File(destPath);
    await file.create(recursive: true);
    await file.writeAsBytes(resp.bodyBytes);
    return file;
  }
}
