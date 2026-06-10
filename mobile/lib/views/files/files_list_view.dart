import 'package:flutter/material.dart';
import 'package:intl/intl.dart' as intl;
import '../../models/remote_file.dart';
import '../../services/api_client_service.dart';
import 'snapshot_details_view.dart';
import 'package:flutter/services.dart';
class FilesListView extends StatefulWidget {
  const FilesListView({super.key});
  @override
  State<FilesListView> createState() => _FilesListViewState();
}
class _FilesListViewState extends State<FilesListView> {
  final ApiClientService _apiClient = ApiClientService();
  late TextEditingController _searchController;
  late ScrollController _scrollController;
  List<RemoteFile> _files = [];
  int _currentPage = 1;
  bool _isLoading = false;
  bool _hasError = false;
  String _errorMessage = '';
  bool _endOfList = false;
  List<Map<String, dynamic>> _deepSearchResults = [];
  bool _isDeepSearching = false;
  bool _isDeepSearchMode = false;
  @override
  void initState() {
    super.initState();
    _searchController = TextEditingController();
    _scrollController = ScrollController();
    _loadFiles();
    _scrollController.addListener(() {
      if (_scrollController.position.pixels >=
          _scrollController.position.maxScrollExtent - 500) {
        _loadMoreFiles();
      }
    });
    _searchController.addListener(_onSearchChanged);
  }
  Future<void> _loadFiles() async {
    if (_isLoading) return;
    try {
      setState(() {
        _isLoading = true;
        _hasError = false;
        _currentPage = 1;
        _endOfList = false;
      });
      final files = await _apiClient.listFiles(
        query: _searchController.text.isNotEmpty ? _searchController.text : null,
        page: 1,
        pageSize: 50,
      );
      if (!mounted) return;
      setState(() {
        _files = files;
        _isLoading = false;
        _endOfList = files.isEmpty;
      });
    } on TokenExpiredException catch (_) {
      if (mounted) {
        setState(() {
          _isLoading = false;
          _hasError = true;
          _errorMessage = 'Sessão expirada. Reinicie o app para fazer login novamente.';
        });
      }
    } on NetworkException {
      if (mounted) {
        setState(() {
          _isLoading = false;
          _hasError = true;
          _errorMessage =
              'Sem conexão com a nuvem.\n\nVerifique se o seu celular está conectado à internet ou à mesma rede do servidor.';
        });
      }
    } on ApiException catch (e) {
      if (mounted) {
        setState(() {
          _isLoading = false;
          _hasError = true;
          _errorMessage = e.message;
        });
      }
    }
  }
  Future<void> _loadMoreFiles() async {
    if (_isLoading || _endOfList) return;
    try {
      setState(() => _isLoading = true);
      final nextPage = _currentPage + 1;
      final files = await _apiClient.listFiles(
        query: _searchController.text.isNotEmpty ? _searchController.text : null,
        page: nextPage,
        pageSize: 50,
      );
      if (!mounted) return;
      setState(() {
        if (files.isEmpty) {
          _endOfList = true;
        } else {
          _files.addAll(files);
          _currentPage = nextPage;
        }
        _isLoading = false;
      });
    } on TokenExpiredException {
      if (mounted) {
        Navigator.of(context).pushNamedAndRemoveUntil('/splash', (_) => false);
      }
    } catch (_) {
      if (mounted) setState(() => _isLoading = false);
    }
  }
  void _onSearchChanged() {
    Future.delayed(const Duration(milliseconds: 500), () {
      if (!mounted) return;
      final query = _searchController.text.trim();
      if (query.length >= 3) {
        _performDeepSearch(query);
      } else {
        setState(() {
          _isDeepSearchMode = false;
          _deepSearchResults = [];
        });
        _loadFiles();
      }
    });
  }
  Future<void> _performDeepSearch(String query) async {
    if (_isDeepSearching) return;
    setState(() {
      _isDeepSearching = true;
      _isDeepSearchMode = true;
      _deepSearchResults = [];
    });
    try {
      final snapshots = await _apiClient.listFiles(page: 1, pageSize: 50);
      List<Map<String, dynamic>> results = [];
      for (final snapshot in snapshots) {
        try {
          final files = await _apiClient.listSnapshotFiles(
            snapshotId: snapshot.id,
            search: query,
            size: 20,
          );
          for (final file in files) {
            results.add({
              'file': file,
              'snapshot': snapshot,
            });
          }
        } catch (_) {
        }
      }
      if (!mounted) return;
      setState(() {
        _deepSearchResults = results;
        _isDeepSearching = false;
      });
    } catch (e) {
      if (!mounted) return;
      setState(() {
        _isDeepSearching = false;
        _hasError = true;
        _errorMessage = 'Erro na busca profunda: $e';
      });
    }
  }
  void _openFilePreview(RemoteFile file) {
    Navigator.push(
      context,
      MaterialPageRoute(
        builder: (context) => SnapshotDetailsView(
          snapshotId: file.id,
          snapshotName: file.name,
        ),
      ),
    );
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

  String _formatDateTime(DateTime date) =>
      intl.DateFormat("dd/MM/yyyy 'às' HH:mm").format(date.toLocal());
  IconData _getFileIcon(String filename) {
    final ext = filename.contains('.')
        ? filename.split('.').last.toLowerCase()
        : '';
    switch (ext) {
      case 'pdf':
        return Icons.picture_as_pdf;
      case 'jpg':
      case 'jpeg':
      case 'png':
      case 'gif':
      case 'webp':
        return Icons.image;
      case 'doc':
      case 'docx':
        return Icons.description;
      case 'xls':
      case 'xlsx':
        return Icons.table_chart;
      case 'ppt':
      case 'pptx':
        return Icons.slideshow;
      case 'zip':
      case 'rar':
      case '7z':
        return Icons.folder_zip;
      case 'mp4':
      case 'avi':
      case 'mov':
      case 'mkv':
        return Icons.video_library;
      case 'mp3':
      case 'wav':
      case 'flac':
        return Icons.audio_file;
      default:
        if (filename.toLowerCase().contains('backup') ||
            filename.toLowerCase().contains('snapshot')) {
          return Icons.backup;
        }
        return Icons.file_present;
    }
  }
  @override
  void dispose() {
    _searchController.dispose();
    _scrollController.dispose();
    super.dispose();
  }
  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: const Color(0xFF08071A),
        appBar: AppBar(
          leading: const SizedBox(),
          leadingWidth: 0,
          automaticallyImplyLeading: false,
          backgroundColor: const Color(0xFF08071A),
          elevation: 0,
          title: Row(
            children: [
              Image.asset(
                'assets/images/keeply_icon.png',
                width: 28,
                height: 28,
              ),
              const SizedBox(width: 10),
              const Text(
                'Keeply',
                style: TextStyle(
                  fontSize: 22,
                  fontWeight: FontWeight.w800,
                  color: Colors.white,
                ),
              ),
            ],
          ),
          actions: [
            IconButton(
              icon: const Icon(Icons.refresh, color: Colors.white70),
              tooltip: 'Recarregar',
              onPressed: () {
                setState(() {
                  _isDeepSearchMode = false;
                  _deepSearchResults = [];
                  _searchController.clear();
                });
                _loadFiles();
              },
            ),
          ],
        ),
        body: Column(
          children: [
            _buildSearchBar(),
            Expanded(
              child: _isDeepSearching
                  ? _buildLoadingWidget()
                  : _isDeepSearchMode
                      ? _deepSearchResults.isEmpty
                          ? _buildEmptyDeepSearchWidget()
                          : _buildDeepSearchResults()
                      : _isLoading && _files.isEmpty
                          ? _buildLoadingWidget()
                          : _hasError
                              ? _buildErrorWidget()
                              : _files.isEmpty
                                  ? _buildEmptyWidget()
                                  : _buildFilesList(),
            ),
          ],
        ),
    );
  }
  Widget _buildSearchBar() {
    return Padding(
      padding: const EdgeInsets.all(16),
      child: TextField(
        controller: _searchController,
        style: const TextStyle(color: Colors.white),
        decoration: InputDecoration(
          hintText: 'Buscar arquivos nos backups...',
          hintStyle: TextStyle(color: Colors.grey[600]),
          prefixIcon: const Icon(Icons.search, color: Color(0xFF7B61FF)),
          suffixIcon: _searchController.text.isNotEmpty
              ? GestureDetector(
                  onTap: () {
                    _searchController.clear();
                    setState(() {
                      _isDeepSearchMode = false;
                      _deepSearchResults = [];
                    });
                    _loadFiles();
                  },
                  child: const Icon(Icons.close, color: Colors.grey),
                )
              : null,
          filled: true,
          fillColor: const Color(0xFF0D0C22),
          border: OutlineInputBorder(
            borderRadius: BorderRadius.circular(12),
            borderSide: BorderSide(color: const Color(0xFF7B61FF).withValues(alpha: 0.3), width: 1),
          ),
          enabledBorder: OutlineInputBorder(
            borderRadius: BorderRadius.circular(12),
            borderSide: BorderSide(color: const Color(0xFF7B61FF).withValues(alpha: 0.3), width: 1),
          ),
          focusedBorder: OutlineInputBorder(
            borderRadius: BorderRadius.circular(12),
            borderSide: const BorderSide(color: Color(0xFF7B61FF), width: 2),
          ),
        ),
      ),
    );
  }
  Widget _buildFilesList() {
    return ListView.builder(
      controller: _scrollController,
      itemCount: _files.length + (_isLoading ? 1 : 0),
      padding: const EdgeInsets.all(8),
      itemBuilder: (context, index) {
        if (index == _files.length) {
          return const Padding(
            padding: EdgeInsets.symmetric(vertical: 16),
            child: Center(
              child: SizedBox(
                width: 32,
                height: 32,
                child: CircularProgressIndicator(
                  strokeWidth: 2,
                  valueColor:
                      AlwaysStoppedAnimation<Color>(Color(0xFF7B61FF)),
                ),
              ),
            ),
          );
        }
        return _buildFileItem(_files[index]);
      },
    );
  }
  Widget _buildFileItem(RemoteFile file) {
    return GestureDetector(
      onTap: () => _openFilePreview(file),
      child: Card(
        color: const Color(0xFF0D0C22),
        margin: const EdgeInsets.symmetric(vertical: 4),
        shape: RoundedRectangleBorder(
          borderRadius: BorderRadius.circular(10),
          side: BorderSide(color: const Color(0xFF7B61FF).withValues(alpha: 0.15)),
        ),
        child: Padding(
          padding: const EdgeInsets.all(12),
          child: Row(
            children: [
              Container(
                width: 48,
                height: 48,
                decoration: BoxDecoration(
                  color: const Color(0xFF7B61FF).withValues(alpha: 0.1),
                  borderRadius: BorderRadius.circular(8),
                ),
                child: file.mimeType == 'application/x-keeply-snapshot'
                    ? const Icon(
                        Icons.auto_awesome,
                        color: Color(0xFF7B61FF),
                        size: 26,
                      )
                    : Icon(
                        _getFileIcon(file.name),
                        color: const Color(0xFFA78BFA),
                        size: 24,
                      ),
              ),
              const SizedBox(width: 12),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      file.name,
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                      style: const TextStyle(
                        fontSize: 14,
                        fontWeight: FontWeight.w600,
                        color: Color(0xFFE2E8F0),
                      ),
                    ),
                    const SizedBox(height: 4),
                    Text(
                      '${_formatDateTime(file.modifiedAt ?? DateTime.now())} • ${_formatFileSize(file.size)}',
                      style: const TextStyle(fontSize: 12, color: Color(0xFF94A3B8)),
                    ),
                  ],
                ),
              ),
              Row(
                children: [
                  IconButton(
                    icon: const Icon(Icons.delete, color: Color(0xFFEF4444)),
                    tooltip: 'Excluir backup',
                    onPressed: () async {
                      final confirmed = await showDialog<bool>(
                        context: context,
                        builder: (ctx) => AlertDialog(
                          backgroundColor: const Color(0xFF0D0C22),
                          title: const Text('Confirmar exclusão', style: TextStyle(color: Colors.white)),
                          content: const Text('Deseja excluir este backup? Esta ação não pode ser desfeita.', style: TextStyle(color: Color(0xFFCBD5E1))),
                          actions: [
                            TextButton(
                              onPressed: () => Navigator.of(ctx).pop(false),
                              child: const Text('Cancelar', style: TextStyle(color: Color(0xFF94A3B8))),
                            ),
                            TextButton(
                              onPressed: () => Navigator.of(ctx).pop(true),
                              child: const Text('Excluir', style: TextStyle(color: Color(0xFFEF4444))),
                            ),
                          ],
                        ),
                      );
                      if (confirmed == true) {
                        try {
                          await _apiClient.deleteSnapshot(file.id);
                          setState(() {
                            _files.removeWhere((f) => f.id == file.id);
                          });
                          ScaffoldMessenger.of(context).showSnackBar(
                            const SnackBar(content: Text('Backup excluído com sucesso'), backgroundColor: Color(0xFF10B981)),
                          );
                        } catch (e) {
                          ScaffoldMessenger.of(context).showSnackBar(
                            SnackBar(content: Text('Erro ao excluir: $e'), backgroundColor: const Color(0xFFEF4444)),
                          );
                        }
                      }
                    },
                  ),
                  const Icon(Icons.chevron_right, color: Color(0xFF64748B)),
                ],
              ),
            ],
          ),
        ),
      ),
    );
  }
  Widget _buildDeepSearchResults() {
    return ListView.builder(
      itemCount: _deepSearchResults.length,
      padding: const EdgeInsets.all(8),
      itemBuilder: (context, index) {
        final result = _deepSearchResults[index];
        final file = result['file'] as RemoteFile;
        final snapshot = result['snapshot'] as RemoteFile;
        return GestureDetector(
          onTap: () => _openFilePreview(snapshot),
          child: Card(
            color: const Color(0xFF0D0C22),
            margin: const EdgeInsets.symmetric(vertical: 4),
            shape: RoundedRectangleBorder(
              borderRadius: BorderRadius.circular(10),
              side: BorderSide(color: const Color(0xFF7B61FF).withValues(alpha: 0.15)),
            ),
            child: Padding(
              padding: const EdgeInsets.all(12),
              child: Row(
                children: [
                  Container(
                    width: 48,
                    height: 48,
                    decoration: BoxDecoration(
                      color: const Color(0xFF7B61FF).withValues(alpha: 0.1),
                      borderRadius: BorderRadius.circular(8),
                    ),
                    child: Icon(
                      _getFileIcon(file.name),
                      color: const Color(0xFFA78BFA),
                      size: 24,
                    ),
                  ),
                  const SizedBox(width: 12),
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(
                          file.name,
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                          style: const TextStyle(
                            fontSize: 14,
                            fontWeight: FontWeight.w600,
                            color: Color(0xFFE2E8F0),
                          ),
                        ),
                        const SizedBox(height: 4),
                        Text(
                          'Encontrado em Backup (${_formatDateTime(snapshot.modifiedAt ?? DateTime.now())})',
                          style: const TextStyle(fontSize: 11, color: Color(0xFF7B61FF)),
                        ),
                        Text(
                          _formatFileSize(file.size),
                          style: const TextStyle(fontSize: 11, color: Color(0xFF94A3B8)),
                        ),
                      ],
                    ),
                  ),
                  const Icon(Icons.chevron_right, color: Color(0xFF64748B)),
                ],
              ),
            ),
          ),
        );
      },
    );
  }
  Widget _buildEmptyDeepSearchWidget() {
    return Center(
      child: Column(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          Icon(Icons.search_off, size: 64, color: Colors.grey[600]),
          const SizedBox(height: 16),
          Text(
            'Nenhum arquivo encontrado',
            style: TextStyle(fontSize: 16, color: Colors.grey[400]),
          ),
          const SizedBox(height: 8),
          Text(
            'Nenhum resultado para "${_searchController.text}"\nnos backups disponíveis.',
            textAlign: TextAlign.center,
            style: TextStyle(fontSize: 12, color: Colors.grey[500]),
          ),
        ],
      ),
    );
  }
  Widget _buildLoadingWidget() {
    return Center(
      child: Column(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          const SizedBox(
            width: 50,
            height: 50,
            child: CircularProgressIndicator(
              valueColor:
                  AlwaysStoppedAnimation<Color>(Color(0xFF7B61FF)),
            ),
          ),
          const SizedBox(height: 16),
          Text(
            _isDeepSearching ? 'Buscando nos backups...' : 'Carregando snapshots...',
            style: TextStyle(fontSize: 14, color: Colors.grey[400]),
          ),
        ],
      ),
    );
  }
  Widget _buildErrorWidget() {
    return Center(
      child: Padding(
        padding: const EdgeInsets.all(32),
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            const Icon(Icons.error_outline, color: Colors.red, size: 48),
            const SizedBox(height: 16),
            const Text(
              'Erro ao carregar',
              style: TextStyle(
                fontSize: 16,
                fontWeight: FontWeight.bold,
                color: Colors.white,
              ),
            ),
            const SizedBox(height: 8),
            Text(
              _errorMessage,
              textAlign: TextAlign.center,
              style: TextStyle(fontSize: 12, color: Colors.grey[400]),
            ),
            const SizedBox(height: 24),
            ElevatedButton.icon(
              onPressed: _loadFiles,
              style: ElevatedButton.styleFrom(
                backgroundColor: const Color(0xFF7B61FF),
              ),
              icon: const Icon(Icons.refresh),
              label: const Text('Tentar Novamente'),
            ),
          ],
        ),
      ),
    );
  }
  Widget _buildEmptyWidget() {
    return Center(
      child: Column(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          Icon(Icons.auto_awesome, size: 64, color: Colors.grey[600]),
          const SizedBox(height: 16),
          Text(
            'Nenhum snapshot encontrado',
            style: TextStyle(fontSize: 16, color: Colors.grey[400]),
          ),
          const SizedBox(height: 8),
          Text(
            _searchController.text.isNotEmpty
                ? 'Tente refinar sua busca'
                : 'Execute um backup no agente Keeply\npara ver seus snapshots aqui.',
            textAlign: TextAlign.center,
            style: TextStyle(fontSize: 12, color: Colors.grey[500]),
          ),
        ],
      ),
    );
  }
}
enum FileFilter {
  recent('Recentes'),
  images('Imagens'),
  documents('Documentos'),
  pdf('PDF');
  final String label;
  const FileFilter(this.label);
}
