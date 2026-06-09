import 'dart:io';

import 'package:path_provider/path_provider.dart';

import '../models/remote_file.dart';
import 'api_client.dart';
import 'network_service.dart';

/// Serviço de download responsável por falar com o backend e salvar o arquivo localmente.
///
/// O app nunca se conecta diretamente ao MinIO. O backend expõe endpoints HTTP para
/// listar arquivos remotos e baixar cada arquivo de forma segura.
class FileDownloadService {
  final String baseUrl;
  final ApiClient _apiClient;

  FileDownloadService({required this.baseUrl})
    : _apiClient = ApiClient(baseUrl: baseUrl);

  Future<List<RemoteFile>> fetchRemoteFiles() async {
    if (!await NetworkService().hasInternetConnection()) {
      throw DownloadException(
        'Sem conexão com a internet. Verifique sua rede e tente novamente.',
      );
    }

    try {
      return await _apiClient.listFiles();
    } catch (e) {
      throw DownloadException('Falha ao listar arquivos: $e');
    }
  }

  Future<File> downloadFile(RemoteFile file) async {
    if (!await NetworkService().hasInternetConnection()) {
      throw DownloadException(
        'Sem conexão com a internet. O download só funciona online.',
      );
    }

    try {
      final dir = await getApplicationDocumentsDirectory();
      final destinationPath = '${dir.path}/${file.name}';
      return await _apiClient.downloadFileToPath(file.id, destinationPath);
    } catch (e) {
      throw DownloadException('Falha no download: $e');
    }
  }

  void dispose() {
    _apiClient.dispose();
  }
}

class DownloadException implements Exception {
  final String message;
  DownloadException(this.message);

  @override
  String toString() => 'DownloadException: $message';
}
