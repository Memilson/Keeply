import 'package:flutter/material.dart';
import 'package:intl/intl.dart' as intl;
import '../../models/remote_file.dart';
import '../../services/api_client_service.dart';
import 'file_preview_modal.dart';

/// [FilesListView] - Tela principal do aplicativo estilo OneDrive.
///
/// Responsabilidades:
/// 1. Exibir lista de arquivos do backend (MinIO via API)
/// 2. Implementar busca em tempo real
/// 3. Fornecer filtros por tipo (Data, Imagens, Documentos, PDF)
/// 4. Mostrar ícone, nome, data e tamanho para cada arquivo
/// 5. Permitir tap para abrir preview modal
/// 6. Implementar infinite scroll / paginação
/// 7. Tratamento de erros de rede / autenticação
///
/// Estrutura da UI:
/// ```
/// ┌─────────────────────────────────────┐
/// │ Keeply                         [Menu]│  ← AppBar
/// ├─────────────────────────────────────┤
/// │ 🔍 Buscar arquivos...              │  ← SearchBar
/// ├─────────────────────────────────────┤
/// │ Recentes  |  Imagens  |  Docs  |... │  ← FilterChips
/// ├─────────────────────────────────────┤
/// │ 📄 documento.pdf                    │
/// │    08/06/2026 • 44 MB               │  ← FileItem
/// ├─────────────────────────────────────┤
/// │ 🖼️  foto.jpg                         │
/// │    06/06/2026 • 2.3 MB              │
/// ├─────────────────────────────────────┤
/// │ ... (infinite scroll)               │
/// └─────────────────────────────────────┘
/// ```
///
/// Integração com Backend:
/// - GET /api/files?page=1&pageSize=50&q=search
/// - Resposta: { "items": [...], "total": 100 }
///
/// Fluxo de Usuário:
/// 1. Tela carrega → busca arquivos via ApiClientService
/// 2. Lista exibida com scroll infinito
/// 3. Usuário digita busca → realiza nova requisição
/// 4. Usuário toca em arquivo → abre FilePreviewModal
/// 5. Modal exibe preview + botão download
///
/// Segurança:
/// - Todas as requisições usam JWT do SecureStorageService
/// - Erros 401 indicam token expirado → logout
/// - Dados sensíveis não são logados
///
/// Uso:
/// ```dart
/// MaterialApp(
///   routes: {
///     '/files': (_) => const FilesListView(),
///   },
/// )
/// ```
class FilesListView extends StatefulWidget {
  const FilesListView({super.key});

  @override
  State<FilesListView> createState() => _FilesListViewState();
}

/// [_FilesListViewState] - Estado e lógica da tela de arquivos.
class _FilesListViewState extends State<FilesListView> {
  /// Cliente de API para buscar arquivos.
  final ApiClientService _apiClient = ApiClientService();

  /// Controller de busca.
  late TextEditingController _searchController;

  /// ScrollController para infinite scroll.
  late ScrollController _scrollController;

  /// Lista de arquivos exibida.
  List<RemoteFile> _files = [];

  /// Página atual (paginação).
  int _currentPage = 1;

  /// Filtro selecionado atualmente.
  FileFilter _selectedFilter = FileFilter.recent;

  /// Flag de carregamento.
  bool _isLoading = false;

  /// Flag de erro.
  bool _hasError = false;
  String _errorMessage = '';

  /// Flag de fim de lista (sem mais itens para carregar).
  bool _endOfList = false;

  @override
  void initState() {
    super.initState();
    _searchController = TextEditingController();
    _scrollController = ScrollController();

    // Carregar arquivos iniciais
    _loadFiles();

    // Listener para infinite scroll
    _scrollController.addListener(() {
      if (_scrollController.position.pixels >=
          _scrollController.position.maxScrollExtent - 500) {
        _loadMoreFiles();
      }
    });

    // Listener para busca com debounce
    _searchController.addListener(_onSearchChanged);
  }

  /// Carrega a primeira página de arquivos.
  ///
  /// Reseta página para 1 e limpa lista anterior.
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
        query: _searchController.text.isNotEmpty
            ? _searchController.text
            : null,
        page: 1,
        pageSize: 50,
      );

      if (!mounted) return;

      setState(() {
        _files = files;
        _isLoading = false;
        _endOfList = files.isEmpty;
      });
    } on TokenExpiredException {
      if (mounted) {
        Navigator.of(context).pushNamedAndRemoveUntil('/splash', (_) => false);
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

  /// Carrega próxima página de arquivos (infinite scroll).
  ///
  /// Append ao final da lista sem resetar.
  Future<void> _loadMoreFiles() async {
    if (_isLoading || _endOfList) return;

    try {
      setState(() {
        _isLoading = true;
      });

      final nextPage = _currentPage + 1;
      final files = await _apiClient.listFiles(
        query: _searchController.text.isNotEmpty
            ? _searchController.text
            : null,
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
    } catch (e) {
      setState(() {
        _isLoading = false;
      });
    }
  }

  /// Debounce para busca (executa após 500ms de pausa).
  void _onSearchChanged() {
    Future.delayed(const Duration(milliseconds: 500), () {
      _loadFiles();
    });
  }

  /// Abre modal de preview do arquivo.
  ///
  /// Parâmetro:
  /// - [file]: Arquivo selecionado
  void _openFilePreview(RemoteFile file) {
    showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      backgroundColor: Colors.transparent,
      builder: (context) => FilePreviewModal(file: file),
    );
  }

  /// Formata tamanho de arquivo em formato legível.
  ///
  /// Exemplos:
  /// - 500 bytes → "500 B"
  /// - 1048576 bytes → "1.0 MB"
  /// - 1073741824 bytes → "1.0 GB"
  String _formatFileSize(int bytes) {
    const suffixes = ['B', 'KB', 'MB', 'GB'];
    double size = bytes.toDouble();

    int suffixIndex = 0;
    while (size > 1024 && suffixIndex < suffixes.length - 1) {
      size /= 1024;
      suffixIndex++;
    }

    return '${size.toStringAsFixed(1)} ${suffixes[suffixIndex]}';
  }

  /// Formata data em formato legível.
  ///
  /// Exemplo: "08/06/2026"
  String _formatDate(DateTime date) {
    return intl.DateFormat('dd/MM/yyyy').format(date);
  }

  /// Retorna ícone apropriado para o tipo de arquivo.
  IconData _getFileIcon(String filename) {
    final ext = filename.split('.').last.toLowerCase();

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
        return Icons.file_present;
    }
  }

  @override
  void dispose() {
    _searchController.dispose();
    _scrollController.dispose();
    _apiClient.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: const Color(0xFF0F172A),
      appBar: AppBar(
        backgroundColor: const Color(0xFF1E293B),
        elevation: 0,
        title: const Text(
          'Keeply',
          style: TextStyle(
            fontSize: 20,
            fontWeight: FontWeight.bold,
            color: Colors.white,
          ),
        ),
        actions: [
          IconButton(
            icon: const Icon(Icons.menu),
            onPressed: () {
              // TODO: Implementar menu (logout, configurações, etc)
            },
          ),
        ],
      ),
      body: Column(
        children: [
          // SearchBar
          _buildSearchBar(),

          // FilterChips
          _buildFilterChips(),

          // Lista de arquivos
          Expanded(
            child: _isLoading && _files.isEmpty
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

  /// Widget da barra de busca.
  Widget _buildSearchBar() {
    return Padding(
      padding: const EdgeInsets.all(16),
      child: TextField(
        controller: _searchController,
        style: const TextStyle(color: Colors.white),
        decoration: InputDecoration(
          hintText: 'Buscar arquivos...',
          hintStyle: TextStyle(color: Colors.grey[600]),
          prefixIcon: const Icon(Icons.search, color: Color(0xFF3B82F6)),
          suffixIcon: _searchController.text.isNotEmpty
              ? GestureDetector(
                  onTap: () {
                    _searchController.clear();
                    _loadFiles();
                  },
                  child: const Icon(Icons.close, color: Colors.grey),
                )
              : null,
          filled: true,
          fillColor: const Color(0xFF1E293B),
          border: OutlineInputBorder(
            borderRadius: BorderRadius.circular(12),
            borderSide: const BorderSide(color: Color(0xFF334155), width: 1),
          ),
          enabledBorder: OutlineInputBorder(
            borderRadius: BorderRadius.circular(12),
            borderSide: const BorderSide(color: Color(0xFF334155), width: 1),
          ),
          focusedBorder: OutlineInputBorder(
            borderRadius: BorderRadius.circular(12),
            borderSide: const BorderSide(color: Color(0xFF3B82F6), width: 2),
          ),
        ),
      ),
    );
  }

  /// Widget dos chips de filtro.
  Widget _buildFilterChips() {
    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
      child: SingleChildScrollView(
        scrollDirection: Axis.horizontal,
        child: Row(
          children: FileFilter.values.map((filter) {
            final isSelected = _selectedFilter == filter;

            return Padding(
              padding: const EdgeInsets.only(right: 8),
              child: FilterChip(
                label: Text(filter.label),
                selected: isSelected,
                onSelected: (selected) {
                  setState(() {
                    _selectedFilter = filter;
                  });
                  _loadFiles();
                },
                backgroundColor: const Color(0xFF1E293B),
                selectedColor: const Color(0xFF3B82F6),
                labelStyle: TextStyle(
                  color: isSelected ? Colors.white : Colors.grey[400],
                  fontWeight: FontWeight.w500,
                ),
                side: BorderSide(
                  color: isSelected
                      ? const Color(0xFF3B82F6)
                      : const Color(0xFF334155),
                  width: 1,
                ),
              ),
            );
          }).toList(),
        ),
      ),
    );
  }

  /// Widget da lista de arquivos.
  Widget _buildFilesList() {
    return ListView.builder(
      controller: _scrollController,
      itemCount: _files.length + (_isLoading ? 1 : 0),
      padding: const EdgeInsets.all(8),
      itemBuilder: (context, index) {
        // Loading indicator no final da lista
        if (index == _files.length) {
          return Padding(
            padding: const EdgeInsets.symmetric(vertical: 16),
            child: Center(
              child: SizedBox(
                width: 32,
                height: 32,
                child: CircularProgressIndicator(
                  strokeWidth: 2,
                  valueColor: const AlwaysStoppedAnimation<Color>(
                    Color(0xFF3B82F6),
                  ),
                ),
              ),
            ),
          );
        }

        final file = _files[index];
        return _buildFileItem(file);
      },
    );
  }

  /// Widget individual de arquivo.
  Widget _buildFileItem(RemoteFile file) {
    return GestureDetector(
      onTap: () => _openFilePreview(file),
      child: Card(
        color: const Color(0xFF1E293B),
        margin: const EdgeInsets.symmetric(vertical: 4, horizontal: 0),
        child: Padding(
          padding: const EdgeInsets.all(12),
          child: Row(
            children: [
              // Ícone do arquivo
              Container(
                width: 48,
                height: 48,
                decoration: BoxDecoration(
                  color: const Color(0xFF0F172A),
                  borderRadius: BorderRadius.circular(8),
                ),
                child: Icon(
                  _getFileIcon(file.name),
                  color: const Color(0xFF3B82F6),
                  size: 24,
                ),
              ),

              const SizedBox(width: 12),

              // Nome e metadados
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    // Nome do arquivo
                    Text(
                      file.name,
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                      style: const TextStyle(
                        fontSize: 14,
                        fontWeight: FontWeight.w500,
                        color: Colors.white,
                      ),
                    ),

                    const SizedBox(height: 4),

                    // Data e tamanho
                    Text(
                      '${_formatDate(file.uploadedAt)} • ${_formatFileSize(file.sizeBytes)}',
                      style: TextStyle(fontSize: 12, color: Colors.grey[500]),
                    ),
                  ],
                ),
              ),

              // Ícone de seta / menu
              Icon(Icons.chevron_right, color: Colors.grey[600]),
            ],
          ),
        ),
      ),
    );
  }

  /// Widget de carregamento.
  Widget _buildLoadingWidget() {
    return Center(
      child: Column(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          const SizedBox(
            width: 50,
            height: 50,
            child: CircularProgressIndicator(
              valueColor: AlwaysStoppedAnimation<Color>(Color(0xFF3B82F6)),
            ),
          ),
          const SizedBox(height: 16),
          Text(
            'Carregando arquivos...',
            style: TextStyle(fontSize: 14, color: Colors.grey[400]),
          ),
        ],
      ),
    );
  }

  /// Widget de erro.
  Widget _buildErrorWidget() {
    return Center(
      child: Padding(
        padding: const EdgeInsets.all(32),
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            const Icon(Icons.error_outline, color: Colors.red, size: 48),
            const SizedBox(height: 16),
            Text(
              'Erro ao carregar arquivos',
              style: const TextStyle(
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
                backgroundColor: const Color(0xFF3B82F6),
              ),
              icon: const Icon(Icons.refresh),
              label: const Text('Tentar Novamente'),
            ),
          ],
        ),
      ),
    );
  }

  /// Widget de lista vazia.
  Widget _buildEmptyWidget() {
    return Center(
      child: Column(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          Icon(Icons.folder_open, size: 64, color: Colors.grey[600]),
          const SizedBox(height: 16),
          Text(
            'Nenhum arquivo encontrado',
            style: TextStyle(fontSize: 16, color: Colors.grey[400]),
          ),
          const SizedBox(height: 8),
          Text(
            _searchController.text.isNotEmpty
                ? 'Tente refinar sua busca'
                : 'Seus arquivos aparecerão aqui',
            style: TextStyle(fontSize: 12, color: Colors.grey[500]),
          ),
        ],
      ),
    );
  }
}

/// Enum para tipos de filtro.
enum FileFilter {
  recent('Recentes'),
  images('Imagens'),
  documents('Documentos'),
  pdf('PDF');

  final String label;
  const FileFilter(this.label);
}
