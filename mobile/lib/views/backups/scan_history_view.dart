import 'dart:io';
import 'package:flutter/material.dart';
import '../../services/media_scanner_service.dart';
import '../../services/permission_service.dart';

class ScanHistoryView extends StatefulWidget {
  const ScanHistoryView({super.key});
  @override
  State<ScanHistoryView> createState() => _ScanHistoryViewState();
}

class _ScanHistoryViewState extends State<ScanHistoryView> {
  final MediaScannerService _scanner = MediaScannerService();
  final PermissionService _permissions = PermissionService();
  List<File> _scannedFiles = [];
  bool _isLoading = false;
  bool _hasPermission = false;
  bool _permissionDenied = false;
  String? _currentPath;

  @override
  void initState() {
    super.initState();
    _checkAndScan();
  }

  Future<void> _checkAndScan() async {
    // Verifica permissão primeiro
    _hasPermission = await _permissions.hasStoragePermission();

    if (_hasPermission) {
      _scanDefaultPath();
    } else {
      // Solicita permissão
      final granted = await _permissions.requestStoragePermission();
      if (mounted) {
        setState(() {
          _hasPermission = granted;
          _permissionDenied = !granted;
        });
      }
      if (granted) {
        _scanDefaultPath();
      }
    }
  }

  Future<void> _scanDefaultPath() async {
    // Tenta vários diretórios padrão
    final paths = [
      '/storage/emulated/0/DCIM',
      '/storage/emulated/0/Pictures',
      '/storage/emulated/0/Download',
      '/storage/emulated/0/Documents',
    ];

    for (final path in paths) {
      final dir = Directory(path);
      if (await dir.exists()) {
        _scanDirectory(path);
        return;
      }
    }

    // Fallback: tenta o storage root
    _scanDirectory('/storage/emulated/0');
  }

  Future<void> _scanDirectory(String path) async {
    setState(() {
      _isLoading = true;
      _currentPath = path;
    });
    try {
      final files = await _scanner.scanDirectory(path);
      if (mounted) {
        setState(() {
          _scannedFiles = files;
          _isLoading = false;
        });
      }
    } catch (e) {
      if (mounted) {
        setState(() {
          _isLoading = false;
        });
      }
    }
  }

  String _formatFileSize(int bytes) {
    if (bytes < 1024) return '$bytes B';
    if (bytes < 1048576) return '${(bytes / 1024).toStringAsFixed(1)} KB';
    if (bytes < 1073741824) return '${(bytes / 1048576).toStringAsFixed(1)} MB';
    return '${(bytes / 1073741824).toStringAsFixed(1)} GB';
  }

  String _getFileName(String path) {
    return path.split('/').last.split('\\').last;
  }

  String _getExtension(String path) {
    if (!path.contains('.')) return '';
    return '.${path.split('.').last.toLowerCase()}';
  }

  @override
  Widget build(BuildContext context) {
    // Se não tem permissão, mostra tela de solicitação
    if (!_hasPermission && _permissionDenied) {
      return _buildPermissionDeniedView();
    }

    final total = _scannedFiles.length;
    final totalSize = _scannedFiles.fold<int>(0, (sum, f) {
      try {
        return sum + f.lengthSync();
      } catch (_) {
        return sum;
      }
    });

    return Scaffold(
      backgroundColor: const Color(0xFF0F172A),
      appBar: AppBar(
        title: const Text(
          'Histórico de Scan',
          style: TextStyle(
            fontWeight: FontWeight.w700,
            fontSize: 18,
            color: Colors.white,
          ),
        ),
        backgroundColor: const Color(0xFF1E293B),
        elevation: 0,
        automaticallyImplyLeading: false,
        actions: [
          IconButton(
            icon: const Icon(
              Icons.refresh_rounded,
              color: Color(0xFF3B82F6),
              size: 22,
            ),
            onPressed: _isLoading ? null : _scanDefaultPath,
            tooltip: 'Reescanear',
          ),
          const SizedBox(width: 8),
        ],
      ),
      body: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          // Stats cards
          Padding(
            padding: const EdgeInsets.all(16),
            child: Row(
              children: [
                Expanded(
                  child: _buildStatCard(
                    Icons.sd_storage_outlined,
                    const Color(0xFF3B82F6),
                    total.toString(),
                    'Arquivos',
                  ),
                ),
                const SizedBox(width: 12),
                Expanded(
                  child: _buildStatCard(
                    Icons.data_usage,
                    const Color(0xFF22C55E),
                    _formatFileSize(totalSize),
                    'Tamanho',
                  ),
                ),
                const SizedBox(width: 12),
                Expanded(
                  child: _buildStatCard(
                    Icons.folder_open,
                    const Color(0xFF8B5CF6),
                    _currentPath != null ? '1' : '0',
                    'Pastas',
                  ),
                ),
              ],
            ),
          ),

          // Path info
          if (_currentPath != null)
            Padding(
              padding: const EdgeInsets.symmetric(horizontal: 16),
              child: Container(
                padding: const EdgeInsets.symmetric(
                  horizontal: 12,
                  vertical: 8,
                ),
                decoration: BoxDecoration(
                  color: const Color(0xFF1E293B),
                  borderRadius: BorderRadius.circular(8),
                  border: Border.all(color: const Color(0xFF334155)),
                ),
                child: Row(
                  children: [
                    const Icon(
                      Icons.folder,
                      color: Color(0xFF3B82F6),
                      size: 16,
                    ),
                    const SizedBox(width: 8),
                    Expanded(
                      child: Text(
                        _currentPath!,
                        style: const TextStyle(
                          color: Color(0xFF94A3B8),
                          fontSize: 12,
                        ),
                        overflow: TextOverflow.ellipsis,
                      ),
                    ),
                    // Botão para escanear outra pasta
                    PopupMenuButton<String>(
                      icon: const Icon(
                        Icons.more_vert,
                        color: Color(0xFF64748B),
                        size: 18,
                      ),
                      color: const Color(0xFF1E293B),
                      onSelected: _scanDirectory,
                      itemBuilder: (context) => [
                        _buildMenuItem(
                          '/storage/emulated/0/DCIM',
                          'DCIM (Câmera)',
                        ),
                        _buildMenuItem(
                          '/storage/emulated/0/Pictures',
                          'Pictures',
                        ),
                        _buildMenuItem(
                          '/storage/emulated/0/Download',
                          'Downloads',
                        ),
                        _buildMenuItem(
                          '/storage/emulated/0/Documents',
                          'Documentos',
                        ),
                        _buildMenuItem('/storage/emulated/0/Movies', 'Vídeos'),
                        _buildMenuItem('/storage/emulated/0', 'Tudo (Raiz)'),
                      ],
                    ),
                  ],
                ),
              ),
            ),
          const SizedBox(height: 12),

          // File list
          Expanded(
            child: _isLoading
                ? const Center(
                    child: Column(
                      mainAxisAlignment: MainAxisAlignment.center,
                      children: [
                        CircularProgressIndicator(color: Color(0xFF3B82F6)),
                        SizedBox(height: 16),
                        Text(
                          'Escaneando...',
                          style: TextStyle(color: Color(0xFF94A3B8)),
                        ),
                      ],
                    ),
                  )
                : _scannedFiles.isEmpty
                ? _buildEmptyState()
                : ListView.separated(
                    padding: const EdgeInsets.symmetric(horizontal: 16),
                    itemCount: _scannedFiles.length,
                    separatorBuilder: (context, index) =>
                        const SizedBox(height: 8),
                    itemBuilder: (context, index) {
                      final file = _scannedFiles[index];
                      final name = _getFileName(file.path);
                      final ext = _getExtension(file.path);
                      int size = 0;
                      try {
                        size = file.lengthSync();
                      } catch (_) {}

                      return Container(
                        padding: const EdgeInsets.all(12),
                        decoration: BoxDecoration(
                          color: const Color(0xFF1E293B),
                          borderRadius: BorderRadius.circular(12),
                          border: Border.all(color: const Color(0xFF334155)),
                        ),
                        child: Row(
                          children: [
                            _buildFileIcon(ext),
                            const SizedBox(width: 12),
                            Expanded(
                              child: Column(
                                crossAxisAlignment: CrossAxisAlignment.start,
                                children: [
                                  Text(
                                    name,
                                    style: const TextStyle(
                                      color: Color(0xFFE2E8F0),
                                      fontSize: 13,
                                    ),
                                    maxLines: 1,
                                    overflow: TextOverflow.ellipsis,
                                  ),
                                  const SizedBox(height: 2),
                                  Text(
                                    _formatFileSize(size),
                                    style: const TextStyle(
                                      color: Color(0xFF64748B),
                                      fontSize: 11,
                                    ),
                                  ),
                                ],
                              ),
                            ),
                            Container(
                              padding: const EdgeInsets.symmetric(
                                horizontal: 8,
                                vertical: 3,
                              ),
                              decoration: BoxDecoration(
                                color: const Color(
                                  0xFF22C55E,
                                ).withValues(alpha: 0.1),
                                borderRadius: BorderRadius.circular(8),
                                border: Border.all(
                                  color: const Color(
                                    0xFF22C55E,
                                  ).withValues(alpha: 0.3),
                                ),
                              ),
                              child: const Row(
                                mainAxisSize: MainAxisSize.min,
                                children: [
                                  Icon(
                                    Icons.check_circle,
                                    color: Color(0xFF22C55E),
                                    size: 12,
                                  ),
                                  SizedBox(width: 4),
                                  Text(
                                    'Local',
                                    style: TextStyle(
                                      color: Color(0xFF22C55E),
                                      fontSize: 10,
                                    ),
                                  ),
                                ],
                              ),
                            ),
                          ],
                        ),
                      );
                    },
                  ),
          ),
        ],
      ),
    );
  }

  Widget _buildEmptyState() {
    return Center(
      child: Column(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          Icon(
            Icons.folder_off_rounded,
            color: const Color(0xFF334155),
            size: 64,
          ),
          const SizedBox(height: 16),
          const Text(
            'Nenhum arquivo encontrado',
            style: TextStyle(color: Color(0xFF94A3B8), fontSize: 16),
          ),
          const SizedBox(height: 8),
          Text(
            _currentPath ?? 'Selecione uma pasta para escanear',
            style: const TextStyle(color: Color(0xFF64748B), fontSize: 12),
          ),
          const SizedBox(height: 20),
          ElevatedButton.icon(
            onPressed: _scanDefaultPath,
            icon: const Icon(Icons.refresh_rounded, size: 18),
            label: const Text('Tentar Novamente'),
            style: ElevatedButton.styleFrom(
              backgroundColor: const Color(0xFF3B82F6),
              foregroundColor: Colors.white,
              shape: RoundedRectangleBorder(
                borderRadius: BorderRadius.circular(10),
              ),
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildPermissionDeniedView() {
    return Scaffold(
      backgroundColor: const Color(0xFF0F172A),
      appBar: AppBar(
        title: const Text(
          'Histórico de Scan',
          style: TextStyle(
            fontWeight: FontWeight.w700,
            fontSize: 18,
            color: Colors.white,
          ),
        ),
        backgroundColor: const Color(0xFF1E293B),
        elevation: 0,
        automaticallyImplyLeading: false,
      ),
      body: Center(
        child: Padding(
          padding: const EdgeInsets.all(32),
          child: Column(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              Container(
                width: 80,
                height: 80,
                decoration: BoxDecoration(
                  color: const Color(0xFFF59E0B).withValues(alpha: 0.15),
                  shape: BoxShape.circle,
                ),
                child: const Icon(
                  Icons.folder_off_rounded,
                  color: Color(0xFFF59E0B),
                  size: 40,
                ),
              ),
              const SizedBox(height: 24),
              const Text(
                'Permissão Necessária',
                style: TextStyle(
                  color: Colors.white,
                  fontSize: 20,
                  fontWeight: FontWeight.w700,
                ),
              ),
              const SizedBox(height: 8),
              const Text(
                'O Keeply precisa de permissão para acessar\nseus arquivos e realizar o scan local.',
                textAlign: TextAlign.center,
                style: TextStyle(color: Color(0xFF94A3B8), fontSize: 14),
              ),
              const SizedBox(height: 24),
              SizedBox(
                width: double.infinity,
                child: ElevatedButton.icon(
                  onPressed: () async {
                    final granted = await _permissions
                        .requestStoragePermission();
                    if (granted && mounted) {
                      setState(() {
                        _hasPermission = true;
                        _permissionDenied = false;
                      });
                      _scanDefaultPath();
                    } else {
                      // Pode ser permanentemente negado
                      final permanent = await _permissions
                          .isStoragePermanentlyDenied();
                      if (permanent && mounted) {
                        _showSettingsDialog();
                      }
                    }
                  },
                  icon: const Icon(Icons.lock_open_rounded, size: 18),
                  label: const Text('Conceder Permissão'),
                  style: ElevatedButton.styleFrom(
                    backgroundColor: const Color(0xFF3B82F6),
                    foregroundColor: Colors.white,
                    padding: const EdgeInsets.symmetric(vertical: 14),
                    shape: RoundedRectangleBorder(
                      borderRadius: BorderRadius.circular(12),
                    ),
                  ),
                ),
              ),
              const SizedBox(height: 12),
              TextButton(
                onPressed: _showSettingsDialog,
                child: const Text(
                  'Abrir Configurações do App',
                  style: TextStyle(color: Color(0xFF64748B), fontSize: 13),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }

  void _showSettingsDialog() {
    showDialog(
      context: context,
      builder: (ctx) => AlertDialog(
        backgroundColor: const Color(0xFF1E293B),
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
        title: const Text(
          'Permissão Negada',
          style: TextStyle(color: Colors.white),
        ),
        content: const Text(
          'A permissão foi negada permanentemente. Você precisa habilitá-la manualmente nas configurações do sistema.',
          style: TextStyle(color: Color(0xFF94A3B8)),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(ctx),
            child: const Text(
              'Cancelar',
              style: TextStyle(color: Color(0xFF64748B)),
            ),
          ),
          ElevatedButton(
            onPressed: () {
              Navigator.pop(ctx);
              _permissions.openSettings();
            },
            style: ElevatedButton.styleFrom(
              backgroundColor: const Color(0xFF3B82F6),
            ),
            child: const Text('Abrir Configurações'),
          ),
        ],
      ),
    );
  }

  PopupMenuItem<String> _buildMenuItem(String path, String label) {
    final isSelected = _currentPath == path;
    return PopupMenuItem<String>(
      value: path,
      child: Row(
        children: [
          Icon(
            isSelected ? Icons.folder : Icons.folder_outlined,
            color: isSelected
                ? const Color(0xFF3B82F6)
                : const Color(0xFF64748B),
            size: 18,
          ),
          const SizedBox(width: 10),
          Text(
            label,
            style: TextStyle(
              color: isSelected
                  ? const Color(0xFF3B82F6)
                  : const Color(0xFFE2E8F0),
              fontSize: 13,
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildStatCard(
    IconData icon,
    Color iconColor,
    String value,
    String label,
  ) {
    return Container(
      padding: const EdgeInsets.all(14),
      decoration: BoxDecoration(
        color: const Color(0xFF1E293B),
        borderRadius: BorderRadius.circular(12),
        border: Border.all(color: const Color(0xFF334155)),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Icon(icon, color: iconColor, size: 18),
          const SizedBox(height: 10),
          Text(
            value,
            style: const TextStyle(
              color: Colors.white,
              fontSize: 18,
              fontWeight: FontWeight.w600,
            ),
          ),
          const SizedBox(height: 2),
          Text(
            label,
            style: const TextStyle(color: Color(0xFF64748B), fontSize: 11),
          ),
        ],
      ),
    );
  }

  Widget _buildFileIcon(String ext) {
    IconData icon;
    Color color;
    if (['.jpg', '.jpeg', '.png', '.gif', '.webp', '.heic'].contains(ext)) {
      icon = Icons.image_outlined;
      color = const Color(0xFF3B82F6);
    } else if (['.mp4', '.mov', '.avi', '.mkv', '.webm'].contains(ext)) {
      icon = Icons.videocam_outlined;
      color = const Color(0xFF8B5CF6);
    } else if (['.pdf', '.doc', '.docx', '.xls', '.xlsx'].contains(ext)) {
      icon = Icons.description_outlined;
      color = const Color(0xFFEF4444);
    } else {
      icon = Icons.insert_drive_file_outlined;
      color = const Color(0xFF64748B);
    }
    return Container(
      width: 40,
      height: 40,
      decoration: BoxDecoration(
        color: color.withValues(alpha: 0.1),
        borderRadius: BorderRadius.circular(10),
      ),
      child: Icon(icon, color: color, size: 20),
    );
  }
}
