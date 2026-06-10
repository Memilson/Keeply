import 'dart:io';
import 'package:flutter/foundation.dart';
class MediaScannerService {
  static const List<String> imageExtensions = [
    'jpg',
    'jpeg',
    'png',
    'gif',
    'bmp',
    'webp',
    'heic',
  ];
  static const List<String> videoExtensions = [
    'mp4',
    'mkv',
    'avi',
    'mov',
    'wmv',
    'flv',
    'webm',
  ];
  static const List<String> documentExtensions = [
    'pdf',
    'doc',
    'docx',
    'xls',
    'xlsx',
    'ppt',
    'pptx',
    'txt',
    'csv',
    'rtf',
  ];
  Future<List<File>> scanDirectory(
    String directoryPath, {
    bool recursive = true,
  }) async {
    final directory = Directory(directoryPath);
    List<File> mediaFiles = [];
    if (!await directory.exists()) {
      debugPrint('⚠️ MediaScanner: O diretório $directoryPath não existe.');
      return mediaFiles;
    }
    debugPrint('🔍 MediaScanner: Escaneando $directoryPath...');
    try {
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
    debugPrint(
      '📊 MediaScanner: Total de arquivos de mídia encontrados: ${mediaFiles.length}',
    );
    return mediaFiles;
  }
  String _getFileExtension(String path) {
    if (!path.contains('.')) return '';
    return path.split('.').last;
  }
  bool _isSupportedFile(String extension) {
    return imageExtensions.contains(extension) ||
        videoExtensions.contains(extension) ||
        documentExtensions.contains(extension);
  }
}
