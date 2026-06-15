import 'dart:convert';
import 'dart:io';
import 'package:flutter/material.dart';
import 'package:intl/intl.dart' as intl;
import 'package:path_provider/path_provider.dart';
import 'package:shared_preferences/shared_preferences.dart';
import '../../models/snapshot_node.dart';
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
  static const Color _azure = Color(0xFF007FFF);
  static const Color _bg = Color(0xFF08071A);
  static const Color _surface = Color(0xFF0D0C22);
  final ApiClientService _apiClient = ApiClientService();
  final SecureStorageService _secureStorage = SecureStorageService();
  final Map<String, bool> _downloadingMap = {};
  List<SnapshotNode> _nodes = [];
  String _currentDir = '';
  bool _isLoading = false;
  bool _hasError = false;
  String _errorMessage = '';

  @override
  void initState() {
    super.initState();
    _loadNodes();
  }

  Future<void> _loadNodes([String dir = '']) async {
    setState(() {
      _isLoading = true;
      _hasError = false;
      _currentDir = dir;
    });
    final prefs = await SharedPreferences.getInstance();
    final cacheKey =
        'cached_snapshot_nodes_${widget.snapshotId}_${dir.replaceAll('/', '_')}';
    final cached = prefs.getString(cacheKey);
    if (cached != null && cached.isNotEmpty) {
      try {
        final decoded = jsonDecode(cached) as List<dynamic>;
        if (mounted) {
          setState(() {
            _nodes = decoded
                .map((e) => SnapshotNode.fromJson(e as Map<String, dynamic>))
                .toList();
            _sortNodes();
            _isLoading = false;
          });
        }
      } catch (_) {}
    }
    try {
      final nodes = await _apiClient.listSnapshotNodes(
        snapshotId: widget.snapshotId,
        dir: dir,
      );
      await prefs.setString(
        cacheKey,
        jsonEncode(nodes.map((e) => e.toJson()).toList()),
      );
      if (!mounted) return;
      setState(() {
        _nodes = nodes;
        _sortNodes();
        _isLoading = false;
      });
    } catch (e) {
      if (!mounted) return;
      setState(() {
        _isLoading = false;
        if (_nodes.isEmpty) {
          _hasError = true;
          _errorMessage = e.toString();
        }
      });
    }
  }

  void _sortNodes() {
    _nodes.sort((a, b) {
      if (a.directory != b.directory) return a.directory ? -1 : 1;
      return a.name.toLowerCase().compareTo(b.name.toLowerCase());
    });
  }

  Future<void> _downloadNode(SnapshotNode node) async {
    final granted = await PermissionService().requestStoragePermission();
    if (!granted) {
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(
          content: Text('Permissão de armazenamento necessária.'),
          backgroundColor: Color(0xFFEF4444),
        ),
      );
      return;
    }
    setState(() => _downloadingMap[node.path] = true);
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
        throw Exception('Pasta de downloads indisponível.');
      }
      final safeName = node.name.isEmpty ? 'arquivo' : node.name;
      final destinationPath = '${downloadsDir.path}/$safeName';
      await _apiClient.downloadFile(
        widget.snapshotId,
        node.path,
        destinationPath,
      );
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text('Baixado em ${downloadsDir.path}/$safeName'),
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
      if (mounted) setState(() => _downloadingMap[node.path] = false);
    }
  }

  String _parentDir() {
    final clean = _currentDir.endsWith('/')
        ? _currentDir.substring(0, _currentDir.length - 1)
        : _currentDir;
    final index = clean.lastIndexOf('/');
    if (index <= 0) return '';
    return '${clean.substring(0, index)}/';
  }

  Future<bool> _handleBack() async {
    if (_currentDir.isEmpty) return true;
    await _loadNodes(_parentDir());
    return false;
  }

  String _formatFileSize(int bytes) {
    if (bytes <= 0) return '—';
    const suffixes = ['B', 'KB', 'MB', 'GB'];
    double size = bytes.toDouble();
    int suffixIndex = 0;
    while (size > 1024 && suffixIndex < suffixes.length - 1) {
      size /= 1024;
      suffixIndex++;
    }
    return '${size.toStringAsFixed(1)} ${suffixes[suffixIndex]}';
  }

  String _formatDate(DateTime? date) {
    if (date == null) return 'Sem data';
    return intl.DateFormat('dd/MM/yyyy HH:mm').format(date.toLocal());
  }

  IconData _fileIcon(String name) {
    final ext = name.contains('.') ? name.split('.').last.toLowerCase() : '';
    switch (ext) {
      case 'pdf':
        return Icons.picture_as_pdf;
      case 'jpg':
      case 'jpeg':
      case 'png':
      case 'webp':
        return Icons.image;
      case 'zip':
      case 'gz':
      case 'rar':
      case '7z':
        return Icons.folder_zip;
      case 'mp4':
      case 'mov':
      case 'mkv':
        return Icons.video_file;
      default:
        return Icons.insert_drive_file;
    }
  }

  @override
  Widget build(BuildContext context) {
    return PopScope(
      canPop: _currentDir.isEmpty,
      onPopInvokedWithResult: (didPop, _) async {
        if (!didPop) await _handleBack();
      },
      child: Scaffold(
        backgroundColor: _bg,
        appBar: AppBar(
          title: Text(
            widget.snapshotName,
            maxLines: 1,
            overflow: TextOverflow.ellipsis,
          ),
          backgroundColor: _bg,
          elevation: 0,
          iconTheme: const IconThemeData(color: Color(0xFFE2E8F0)),
          actions: [
            IconButton(
              icon: const Icon(Icons.refresh),
              onPressed: () => _loadNodes(_currentDir),
            ),
          ],
        ),
        body: Column(
          children: [
            _buildPathBar(),
            Expanded(child: _buildBody()),
          ],
        ),
      ),
    );
  }

  Widget _buildPathBar() {
    return Container(
      width: double.infinity,
      margin: const EdgeInsets.fromLTRB(12, 4, 12, 8),
      padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 10),
      decoration: BoxDecoration(
        color: _surface,
        borderRadius: BorderRadius.circular(8),
        border: Border.all(color: _azure.withValues(alpha: 0.25)),
      ),
      child: Row(
        children: [
          IconButton(
            icon: Icon(
              _currentDir.isEmpty ? Icons.folder_open : Icons.arrow_upward,
              color: _azure,
            ),
            onPressed: _currentDir.isEmpty
                ? null
                : () => _loadNodes(_parentDir()),
          ),
          const SizedBox(width: 4),
          Expanded(
            child: Text(
              _currentDir.isEmpty ? 'Raiz do snapshot' : _currentDir,
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
              style: const TextStyle(color: Color(0xFFE2E8F0), fontSize: 13),
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildBody() {
    if (_isLoading && _nodes.isEmpty) {
      return const Center(child: CircularProgressIndicator(color: _azure));
    }
    if (_hasError) {
      return Center(
        child: Padding(
          padding: const EdgeInsets.all(24),
          child: Column(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              const Icon(
                Icons.error_outline,
                color: Color(0xFFEF4444),
                size: 44,
              ),
              const SizedBox(height: 12),
              const Text(
                'Erro ao carregar pasta',
                style: TextStyle(color: Colors.white, fontSize: 16),
              ),
              const SizedBox(height: 8),
              Text(
                _errorMessage,
                textAlign: TextAlign.center,
                style: const TextStyle(color: Color(0xFF94A3B8), fontSize: 12),
              ),
              const SizedBox(height: 16),
              FilledButton(
                onPressed: () => _loadNodes(_currentDir),
                child: const Text('Tentar novamente'),
              ),
            ],
          ),
        ),
      );
    }
    if (_nodes.isEmpty) {
      return const Center(
        child: Text(
          'Esta pasta está vazia.',
          style: TextStyle(color: Color(0xFF94A3B8)),
        ),
      );
    }
    return ListView.builder(
      padding: const EdgeInsets.fromLTRB(12, 0, 12, 16),
      itemCount: _nodes.length,
      itemBuilder: (context, index) => _buildNodeItem(_nodes[index]),
    );
  }

  Widget _buildNodeItem(SnapshotNode node) {
    final downloading = _downloadingMap[node.path] ?? false;
    return Card(
      color: _surface,
      margin: const EdgeInsets.symmetric(vertical: 4),
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(8),
        side: BorderSide(color: _azure.withValues(alpha: 0.14)),
      ),
      child: ListTile(
        contentPadding: const EdgeInsets.symmetric(horizontal: 12, vertical: 4),
        leading: Icon(
          node.directory ? Icons.folder : _fileIcon(node.name),
          color: node.directory ? _azure : const Color(0xFFCBD5E1),
        ),
        title: Text(
          node.name,
          maxLines: 1,
          overflow: TextOverflow.ellipsis,
          style: const TextStyle(color: Color(0xFFE2E8F0), fontSize: 14),
        ),
        subtitle: Text(
          node.directory
              ? 'Pasta'
              : '${_formatFileSize(node.size)} • ${_formatDate(node.modifiedAt)}',
          style: const TextStyle(color: Color(0xFF94A3B8), fontSize: 12),
        ),
        trailing: node.directory
            ? const Icon(Icons.chevron_right, color: Color(0xFF64748B))
            : downloading
            ? const SizedBox(
                width: 22,
                height: 22,
                child: CircularProgressIndicator(strokeWidth: 2, color: _azure),
              )
            : IconButton(
                icon: const Icon(Icons.download, color: _azure),
                onPressed: () => _downloadNode(node),
              ),
        onTap: node.directory ? () => _loadNodes(node.path) : null,
      ),
    );
  }
}
