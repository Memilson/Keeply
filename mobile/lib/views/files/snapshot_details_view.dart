import 'package:flutter/material.dart';
import 'package:intl/intl.dart' as intl;
import 'package:path_provider/path_provider.dart';
import 'dart:io';
import '../../models/remote_file.dart';
import '../../services/api_client_service.dart';
import '../../services/permission_service.dart';
import '../../services/secure_storage_service.dart';
class SnapshotDetailsView extends StatefulWidget {
  final String snapshotId;
  final String snapshotName;
  const SnapshotDetailsView({
    super.key,
    required this.snapshotId,
    required this.snapshotName,
  });
  @override
  State<SnapshotDetailsView> createState() => _SnapshotDetailsViewState();
}
class _SnapshotDetailsViewState extends State<SnapshotDetailsView> {
  final ApiClientService _apiClient = ApiClientService();
  final SecureStorageService _secureStorage = SecureStorageService();
  List<RemoteFile> _files = [];
  bool _isLoading = false;
  bool _hasError = false;
  String _errorMessage = '';
  final Map<String, bool> _downloadingMap = {};
  @override
  void initState() {
    super.initState();
    _loadFiles();
  }
  Future<void> _loadFiles() async {
    setState(() {
      _isLoading = true;
      _hasError = false;
    });
    try {
      final files = await _apiClient.listSnapshotFiles(snapshotId: widget.snapshotId);
      if (!mounted) return;
      setState(() {
        _files = files;
        _isLoading = false;
      });
    } catch (e) {
      if (!mounted) return;
      setState(() {
        _hasError = true;
        _errorMessage = e.toString();
        _isLoading = false;
      });
    }
  }
  Future<void> _downloadFile(RemoteFile file) async {
    final isGranted = await PermissionService().requestStoragePermission();
    if (!isGranted) {
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(
          content: Text('Permissão de armazenamento necessária.'),
          backgroundColor: Colors.red,
        ),
      );
      return;
    }
    setState(() {
      _downloadingMap[file.name] = true;
    });
    try {
      final customDir = await _secureStorage.getDownloadDir();
      Directory? downloadsDir;
      if (customDir != null && customDir.isNotEmpty) {
        downloadsDir = Directory(customDir);
        if (!await downloadsDir.exists()) {
          await downloadsDir.create(recursive: true);
        }
      } else if (Platform.isAndroid) {
        downloadsDir = Directory('/storage/emulated/0/Download');
        if (!await downloadsDir.exists()) {
          downloadsDir = await getExternalStorageDirectory();
        }
      } else {
        downloadsDir = await getDownloadsDirectory();
      }
      if (downloadsDir == null) {
        throw Exception("Não foi possível acessar a pasta de downloads.");
      }
      final destinationPath = '${downloadsDir.path}/${file.name}';
      final filePath = file.path.isNotEmpty ? file.path : file.name;
      await _apiClient.downloadFile(widget.snapshotId, filePath, destinationPath);
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text('Baixado em ${downloadsDir.path}/${file.name}'),
          backgroundColor: const Color(0xFF10B981),
        ),
      );
    } catch (e) {
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text('Erro ao baixar: $e'),
          backgroundColor: const Color(0xFFEF4444),
        ),
      );
    } finally {
      if (mounted) {
        setState(() {
          _downloadingMap[file.name] = false;
        });
      }
    }
  }
  String _formatFileSize(int bytes) {
    if (bytes <= 0) return '— B';
    const suffixes = ['B', 'KB', 'MB', 'GB'];
    double size = bytes.toDouble();
    int suffixIndex = 0;
    while (size > 1024 && suffixIndex < suffixes.length - 1) {
      size /= 1024;
      suffixIndex++;
    }
    return '${size.toStringAsFixed(1)} ${suffixes[suffixIndex]}';
  }
  String _formatDate(DateTime date) {
    return intl.DateFormat('dd/MM/yyyy HH:mm').format(date.toLocal());
  }
  IconData _getFileIcon(String filename) {
    final ext = filename.contains('.') ? filename.split('.').last.toLowerCase() : '';
    switch (ext) {
      case 'pdf': return Icons.picture_as_pdf;
      case 'jpg': case 'jpeg': case 'png': return Icons.image;
      case 'doc': case 'docx': return Icons.description;
      case 'xls': case 'xlsx': return Icons.table_chart;
      case 'zip': return Icons.folder_zip;
      default: return Icons.insert_drive_file;
    }
  }
  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: const Color(0xFF08071A),
      appBar: AppBar(
        title: Text(widget.snapshotName),
        backgroundColor: const Color(0xFF08071A),
        elevation: 0,
        iconTheme: const IconThemeData(color: Color(0xFFE2E8F0)),
      ),
      body: _isLoading
          ? const Center(child: CircularProgressIndicator(color: Color(0xFF7B61FF)))
          : _hasError
              ? Center(
                  child: Column(
                    mainAxisAlignment: MainAxisAlignment.center,
                    children: [
                      const Icon(Icons.error_outline, color: Colors.red, size: 48),
                      const SizedBox(height: 16),
                      const Text('Erro ao carregar arquivos', style: TextStyle(color: Colors.white, fontSize: 18)),
                      Text(_errorMessage, style: const TextStyle(color: Colors.grey)),
                      const SizedBox(height: 16),
                      ElevatedButton(
                        onPressed: _loadFiles,
                        child: const Text('Tentar Novamente'),
                      )
                    ],
                  ),
                )
              : _files.isEmpty
                  ? const Center(child: Text('Nenhum arquivo encontrado neste backup.', style: TextStyle(color: Colors.grey)))
                  : ListView.builder(
                      itemCount: _files.length,
                      itemBuilder: (context, index) {
                        final file = _files[index];
                        final isDownloading = _downloadingMap[file.name] ?? false;
                        return Card(
                          color: const Color(0xFF0D0C22),
                          margin: const EdgeInsets.symmetric(horizontal: 12, vertical: 6),
                          shape: RoundedRectangleBorder(
                            borderRadius: BorderRadius.circular(12),
                            side: BorderSide(color: const Color(0xFF7B61FF).withValues(alpha: 0.15)),
                          ),
                          child: ListTile(
                            leading: Icon(_getFileIcon(file.name), color: const Color(0xFFA78BFA)),
                            title: Text(file.name, style: const TextStyle(color: Color(0xFFE2E8F0))),
                            subtitle: Text(
                              '${_formatDate(file.modifiedAt ?? DateTime.now())} • ${_formatFileSize(file.size)}',
                              style: const TextStyle(color: Color(0xFF94A3B8)),
                            ),
                            trailing: isDownloading
                                    ? const SizedBox(width: 24, height: 24, child: CircularProgressIndicator(strokeWidth: 2, color: Color(0xFF7B61FF)))
                                    : IconButton(
                                        icon: const Icon(Icons.download, color: Color(0xFF7B61FF)),
                                        onPressed: () => _downloadFile(file),
                                      ),
                          ),
                        );
                      },
                    ),
    );
  }
}
