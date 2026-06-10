import 'package:flutter/material.dart';
import '../../models/remote_file.dart';
import '../../services/auth_service.dart';
import '../../services/file_download_service.dart';
import '../../services/permission_service.dart';
class FileDownloadView extends StatefulWidget {
  final String backendBaseUrl;
  const FileDownloadView({super.key, required this.backendBaseUrl});
  @override
  State<FileDownloadView> createState() => _FileDownloadViewState();
}
class _FileDownloadViewState extends State<FileDownloadView> {
  late final FileDownloadService _service;
  late final AuthService _authService;
  final PermissionService _permissions = PermissionService();
  Future<List<RemoteFile>>? _filesFuture;
  String? _activeDownloadId;
  String? _statusMessage;
  bool _isLoading = true;
  bool _authenticated = false;
  @override
  void initState() {
    super.initState();
    _service = FileDownloadService(baseUrl: widget.backendBaseUrl);
    _authService = AuthService();
    _initializeScreen();
  }
  @override
  void dispose() {
    _service.dispose();
    super.dispose();
  }
  Future<void> _initializeScreen() async {
    try {
      final storageGranted = await _permissions.requestStoragePermission();
      final cameraGranted = await _permissions.requestCameraPermission();
      if (!storageGranted || !cameraGranted) {
        setState(() {
          _statusMessage =
              'Permissões de câmera e armazenamento são necessárias para visualizar e baixar arquivos.';
          _isLoading = false;
        });
        return;
      }
      final authenticated = await _authService.authenticateBiometric(
        reason: 'Autentique-se para acessar os arquivos remotos.',
      );
      if (!authenticated) {
        setState(() {
          _statusMessage =
              'Autenticação biométrica falhou ou não está disponível. Não é possível acessar os arquivos remotos.';
          _isLoading = false;
        });
        return;
      }
      setState(() {
        _authenticated = true;
        _filesFuture = _service.fetchRemoteFiles();
        _isLoading = false;
      });
    } catch (error) {
      setState(() {
        _statusMessage = 'Erro ao inicializar: $error';
        _isLoading = false;
      });
    }
  }
  void _showMessage(String message, {bool error = false}) {
    final snackBar = SnackBar(
      content: Text(message),
      backgroundColor: error ? Colors.redAccent : Colors.green,
      duration: const Duration(seconds: 3),
    );
    ScaffoldMessenger.of(context).showSnackBar(snackBar);
  }
  Future<void> _handleDownload(RemoteFile file) async {
    if (!_authenticated) {
      _showMessage(
        'Autenticação obrigatória antes de baixar arquivos.',
        error: true,
      );
      return;
    }
    setState(() => _activeDownloadId = file.id);
    try {
      await _service.downloadFile(file);
      _showMessage('Download concluído: ${file.name}');
    } on DownloadException catch (error) {
      _showMessage('Erro de download: ${error.message}', error: true);
    } on Exception catch (error) {
      _showMessage('Erro inesperado: ${error.toString()}', error: true);
    } finally {
      if (mounted) {
        setState(() => _activeDownloadId = null);
      }
    }
  }
  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: const Color(0xFF0F172A),
      appBar: AppBar(
        automaticallyImplyLeading: false,
        title: const Text(
          'Arquivos Remotos',
          style: TextStyle(color: Colors.white, fontWeight: FontWeight.w700),
        ),
        backgroundColor: const Color(0xFF1E293B),
        elevation: 0,
      ),
      body: _isLoading
          ? const Center(child: CircularProgressIndicator())
          : _filesFuture == null
          ? _buildError(_statusMessage ?? 'Aguardando autenticação...')
          : FutureBuilder<List<RemoteFile>>(
              future: _filesFuture,
              builder: (context, snapshot) {
                if (snapshot.connectionState == ConnectionState.waiting) {
                  return const Center(child: CircularProgressIndicator());
                }
                if (snapshot.hasError) {
                  return _buildError(snapshot.error.toString());
                }
                final files = snapshot.data ?? [];
                if (files.isEmpty) {
                  return _buildEmptyState();
                }
                return ListView.separated(
                  padding: const EdgeInsets.all(16),
                  itemCount: files.length,
                  separatorBuilder: (context, index) =>
                      const SizedBox(height: 12),
                  itemBuilder: (context, index) {
                    final file = files[index];
                    final isDownloading = _activeDownloadId == file.id;
                    return Card(
                      color: const Color(0xFF1E293B),
                      shape: RoundedRectangleBorder(
                        borderRadius: BorderRadius.circular(16),
                      ),
                      child: ListTile(
                        title: Text(
                          file.name,
                          style: const TextStyle(
                            color: Colors.white,
                            fontWeight: FontWeight.w600,
                          ),
                        ),
                        subtitle: Text(
                          '${file.size} bytes • ${file.mimeType}',
                          style: const TextStyle(
                            color: Color(0xFF94A3B8),
                            fontSize: 12,
                          ),
                        ),
                        trailing: ElevatedButton.icon(
                          onPressed: isDownloading
                              ? null
                              : () => _handleDownload(file),
                          icon: isDownloading
                              ? const SizedBox(
                                  width: 16,
                                  height: 16,
                                  child: CircularProgressIndicator(
                                    strokeWidth: 2,
                                    color: Colors.white,
                                  ),
                                )
                              : const Icon(Icons.download_outlined),
                          label: Text(isDownloading ? 'Baixando' : 'Download'),
                        ),
                      ),
                    );
                  },
                );
              },
            ),
    );
  }
  Widget _buildEmptyState() {
    return const Center(
      child: Text(
        'Nenhum arquivo disponível para download.',
        textAlign: TextAlign.center,
        style: TextStyle(color: Colors.white70, fontSize: 16),
      ),
    );
  }
  Widget _buildError(String message) {
    return Center(
      child: Padding(
        padding: const EdgeInsets.symmetric(horizontal: 24),
        child: Text(
          'Erro: $message',
          textAlign: TextAlign.center,
          style: const TextStyle(color: Colors.redAccent, fontSize: 15),
        ),
      ),
    );
  }
}
