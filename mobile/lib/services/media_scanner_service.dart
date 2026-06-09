import 'dart:io';
import 'package:flutter/foundation.dart';

/// [MediaScannerService] - Serviço responsável por escanear arquivos no dispositivo
class MediaScannerService {
  
  /// Lista de extensões suportadas para imagens
  static const List<String> imageExtensions = [
    'jpg', 'jpeg', 'png', 'gif', 'bmp', 'webp', 'heic'
  ];

  /// Lista de extensões suportadas para vídeos
  static const List<String> videoExtensions = [
    'mp4', 'mkv', 'avi', 'mov', 'wmv', 'flv', 'webm'
  ];

  /// Lista de extensões suportadas para documentos
  static const List<String> documentExtensions = [
    'pdf', 'doc', 'docx', 'xls', 'xlsx', 'ppt', 'pptx', 'txt', 'csv', 'rtf'
  ];

  /// Faz uma varredura em um diretório específico procurando por fotos e vídeos.
  /// 
  /// Parâmetros:
  /// - [directoryPath]: O caminho da pasta a ser verificada no dispositivo
  /// - [recursive]: Se verdadeiro, também verifica as subpastas
  Future<List<File>> scanDirectory(String directoryPath, {bool recursive = true}) async {
    final directory = Directory(directoryPath);
    List<File> mediaFiles = [];

    // Verifica se o diretório existe
    if (!await directory.exists()) {
      debugPrint('⚠️ MediaScanner: O diretório $directoryPath não existe.');
      return mediaFiles;
    }

    debugPrint('🔍 MediaScanner: Escaneando $directoryPath...');

    try {
      // Stream que lista o conteúdo do diretório
      final stream = directory.list(recursive: recursive, followLinks: false);
      
      await for (final FileSystemEntity entity in stream) {
        if (entity is File) {
          final extension = _getFileExtension(entity.path).toLowerCase();
          
          if (_isSupportedFile(extension)) {
            mediaFiles.add(entity);
            debugPrint('✅ Encontrado: ${entity.path}');
          }
        }
      }
    } catch (e) {
      debugPrint('❌ MediaScanner: Erro ao escanear o diretório - $e');
    }

    debugPrint('📊 MediaScanner: Total de arquivos de mídia encontrados: ${mediaFiles.length}');
    return mediaFiles;
  }

  /// Retorna a extensão do arquivo baseada no caminho
  String _getFileExtension(String path) {
    if (!path.contains('.')) return '';
    return path.split('.').last;
  }

  /// Verifica se a extensão pertence a um arquivo suportado (foto, vídeo ou documento)
  bool _isSupportedFile(String extension) {
    return imageExtensions.contains(extension) || 
           videoExtensions.contains(extension) || 
           documentExtensions.contains(extension);
  }
}
