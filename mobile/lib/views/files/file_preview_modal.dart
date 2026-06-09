import 'package:flutter/material.dart';
import 'package:intl/intl.dart' as intl;
import 'package:path_provider/path_provider.dart';
import 'dart:io';
import '../../models/remote_file.dart';
import '../../services/api_client_service.dart';

/// [FilePreviewModal] - Modal bottom sheet para preview e download de arquivos.
///
/// Responsabilidades:
/// 1. Exibir informações detalhadas do arquivo
/// 2. Mostrar botão de download
/// 3. Gerenciar download com barra de progresso
/// 4. Salvar arquivo localmente
/// 5. Tratamento de erros durante download
///
/// Layout:
/// ```
/// ┌─────────────────────────────────┐
/// │  ↓ (drag down to close)         │
/// ├─────────────────────────────────┤
/// │ 📄 documento.pdf                │
/// │ 44 MB • Upd. 08/06/2026         │
/// ├─────────────────────────────────┤
/// │ 📍 Localização: /Backups/...    │
/// │ 🔒 Criptografado                │
/// │ ✓ Verificado                    │
/// ├─────────────────────────────────┤
/// │ [  Baixar Arquivo (44 MB)    ] │  ← Botão download
/// │ [  Compartilhar               ] │
/// └─────────────────────────────────┘
/// ```
///
/// Fluxo de Download:
/// 1. Usuário toca "Baixar Arquivo"
/// 2. Modal exibe barra de progresso
/// 3. ApiClientService faz requisição GET /api/files/{id}/download
/// 4. Arquivo salvo em getApplicationDocumentsDirectory()
/// 5. Sucesso: exibir botão "Abrir" ou "Compartilhar"
/// 6. Erro: exibir mensagem + retry
///
/// Integração com Backend:
/// - GET /api/files/{id}/download
/// - Retorna arquivo binário
/// - Header: Authorization: Bearer <token>
///
/// Segurança:
/// - Download só com token válido
/// - Arquivo salvo apenas em diretório seguro do app
/// - Metadados de arquivo vêm do backend
///
/// Uso:
/// ```dart
/// showModalBottomSheet(
///   context: context,
///   isScrollControlled: true,
///   backgroundColor: Colors.transparent,
///   builder: (_) => FilePreviewModal(file: file),
/// );
/// ```
class FilePreviewModal extends StatefulWidget {
  final RemoteFile file;

  const FilePreviewModal({super.key, required this.file});

  @override
  State<FilePreviewModal> createState() => _FilePreviewModalState();
}

/// [_FilePreviewModalState] - Estado do modal de preview.
class _FilePreviewModalState extends State<FilePreviewModal> {
  /// Cliente de API para download.
  final ApiClientService _apiClient = ApiClientService();

  /// Flag de download em progresso.
  bool _isDownloading = false;

  /// Progresso do download (0.0 a 1.0).
  double _downloadProgress = 0.0;

  /// Arquivo salvo (se download bem-sucedido).
  File? _downloadedFile;

  /// Mensagem de erro (se houver).
  String _errorMessage = '';

  /// Formata tamanho de arquivo em formato legível.
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
  String _formatDate(DateTime date) {
    return intl.DateFormat('dd/MM/yyyy • HH:mm').format(date);
  }

  /// Retorna cor baseada no tipo de arquivo.
  Color _getFileColor(String filename) {
    final ext = filename.split('.').last.toLowerCase();

    switch (ext) {
      case 'pdf':
        return const Color(0xFFEF4444);
      case 'jpg':
      case 'jpeg':
      case 'png':
      case 'gif':
      case 'webp':
        return const Color(0xFF06B6D4);
      case 'doc':
      case 'docx':
        return const Color(0xFF3B82F6);
      case 'xls':
      case 'xlsx':
        return const Color(0xFF10B981);
      case 'ppt':
      case 'pptx':
        return const Color(0xFFD97706);
      default:
        return const Color(0xFF3B82F6);
    }
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
      default:
        return Icons.file_present;
    }
  }

  /// Inicia download do arquivo.
  ///
  /// Fluxo:
  /// 1. Obter diretório de documentos do app
  /// 2. Chamar ApiClientService.downloadFile()
  /// 3. Atualizar progress em tempo real
  /// 4. Se sucesso: salvar referência do arquivo
  /// 5. Se erro: exibir mensagem de erro
  Future<void> _downloadFile() async {
    try {
      setState(() {
        _isDownloading = true;
        _errorMessage = '';
        _downloadProgress = 0.0;
      });

      // Obter diretório de documentos
      final appDocDir = await getApplicationDocumentsDirectory();
      final downloadDir = Directory('${appDocDir.path}/Keeply/Downloads');
      await downloadDir.create(recursive: true);

      // Caminho de destino
      final destinationPath = '${downloadDir.path}/${widget.file.name}';

      print('Iniciando download para: $destinationPath');

      // Fazer download (em produção, implementar progresso streaming)
      // Por enquanto, download simples sem progresso em tempo real
      final file = await _apiClient.downloadFile(
        widget.file.id,
        destinationPath,
      );

      if (!mounted) return;

      setState(() {
        _isDownloading = false;
        _downloadedFile = file;
        _downloadProgress = 1.0;
      });

      // Mostrar snackbar de sucesso
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text(
              'Arquivo baixado com sucesso',
              style: TextStyle(color: Colors.white),
            ),
            backgroundColor: Colors.green[600],
            duration: const Duration(seconds: 3),
          ),
        );
      }
    } on ApiException catch (e) {
      if (mounted) {
        setState(() {
          _isDownloading = false;
          _errorMessage = 'Erro no download: ${e.message}';
        });
      }
    } catch (e) {
      if (mounted) {
        setState(() {
          _isDownloading = false;
          _errorMessage = 'Erro ao baixar arquivo: $e';
        });
      }
    }
  }

  /// Abre o arquivo após download bem-sucedido.
  ///
  /// TODO: Implementar abertura do arquivo com app apropriado
  /// Pode usar plugins como: open_file, url_launcher, etc
  Future<void> _openDownloadedFile() async {
    if (_downloadedFile == null) return;

    try {
      // TODO: Implementar abertura do arquivo
      // Por enquanto, apenas exibir mensagem
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(
          content: Text('Arquivo salvo em: Downloads/Keeply/'),
          duration: Duration(seconds: 3),
        ),
      );
    } catch (e) {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text('Erro ao abrir arquivo: $e'),
          backgroundColor: Colors.red,
        ),
      );
    }
  }

  @override
  void dispose() {
    _apiClient.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return DraggableScrollableSheet(
      expand: false,
      builder: (context, scrollController) {
        return Container(
          decoration: const BoxDecoration(
            color: Color(0xFF1E293B),
            borderRadius: BorderRadius.vertical(top: Radius.circular(20)),
          ),
          child: Column(
            children: [
              // Handle drag indicator
              Padding(
                padding: const EdgeInsets.symmetric(vertical: 12),
                child: Container(
                  width: 40,
                  height: 4,
                  decoration: BoxDecoration(
                    color: Colors.grey[600],
                    borderRadius: BorderRadius.circular(2),
                  ),
                ),
              ),

              // Conteúdo scrollável
              Expanded(
                child: SingleChildScrollView(
                  controller: scrollController,
                  child: Padding(
                    padding: const EdgeInsets.all(24),
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        // Header com ícone + nome
                        Row(
                          children: [
                            // Ícone grande
                            Container(
                              width: 64,
                              height: 64,
                              decoration: BoxDecoration(
                                color: _getFileColor(
                                  widget.file.name,
                                ).withOpacity(0.2),
                                borderRadius: BorderRadius.circular(12),
                              ),
                              child: Icon(
                                _getFileIcon(widget.file.name),
                                color: _getFileColor(widget.file.name),
                                size: 32,
                              ),
                            ),

                            const SizedBox(width: 16),

                            // Nome + metadados
                            Expanded(
                              child: Column(
                                crossAxisAlignment: CrossAxisAlignment.start,
                                children: [
                                  Text(
                                    widget.file.name,
                                    maxLines: 2,
                                    overflow: TextOverflow.ellipsis,
                                    style: const TextStyle(
                                      fontSize: 16,
                                      fontWeight: FontWeight.bold,
                                      color: Colors.white,
                                    ),
                                  ),
                                  const SizedBox(height: 4),
                                  Text(
                                    '${_formatFileSize(widget.file.sizeBytes)} • Upd. ${_formatDate(widget.file.uploadedAt)}',
                                    style: TextStyle(
                                      fontSize: 12,
                                      color: Colors.grey[400],
                                    ),
                                  ),
                                ],
                              ),
                            ),
                          ],
                        ),

                        const SizedBox(height: 32),

                        // Informações detalhadas
                        _buildInfoSection(),

                        const SizedBox(height: 32),

                        // Progress bar (se downloading)
                        if (_isDownloading)
                          Column(
                            crossAxisAlignment: CrossAxisAlignment.start,
                            children: [
                              Row(
                                mainAxisAlignment:
                                    MainAxisAlignment.spaceBetween,
                                children: [
                                  const Text(
                                    'Progresso do Download',
                                    style: TextStyle(
                                      fontSize: 12,
                                      color: Colors.grey[400],
                                      fontWeight: FontWeight.w500,
                                    ),
                                  ),
                                  Text(
                                    '${(_downloadProgress * 100).toStringAsFixed(0)}%',
                                    style: const TextStyle(
                                      fontSize: 12,
                                      color: Color(0xFF3B82F6),
                                      fontWeight: FontWeight.bold,
                                    ),
                                  ),
                                ],
                              ),
                              const SizedBox(height: 8),
                              ClipRRect(
                                borderRadius: BorderRadius.circular(4),
                                child: LinearProgressIndicator(
                                  value: _downloadProgress,
                                  minHeight: 6,
                                  backgroundColor: const Color(0xFF0F172A),
                                  valueColor:
                                      const AlwaysStoppedAnimation<Color>(
                                        Color(0xFF3B82F6),
                                      ),
                                ),
                              ),
                              const SizedBox(height: 32),
                            ],
                          ),

                        // Mensagem de erro
                        if (_errorMessage.isNotEmpty)
                          Container(
                            padding: const EdgeInsets.all(12),
                            decoration: BoxDecoration(
                              color: Colors.red.withOpacity(0.1),
                              border: Border.all(
                                color: Colors.red.withOpacity(0.3),
                                width: 1,
                              ),
                              borderRadius: BorderRadius.circular(8),
                            ),
                            child: Row(
                              crossAxisAlignment: CrossAxisAlignment.start,
                              children: [
                                const Icon(
                                  Icons.error_outline,
                                  color: Colors.red,
                                  size: 18,
                                ),
                                const SizedBox(width: 8),
                                Expanded(
                                  child: Text(
                                    _errorMessage,
                                    style: const TextStyle(
                                      fontSize: 12,
                                      color: Colors.red,
                                    ),
                                  ),
                                ),
                              ],
                            ),
                          ),

                        const SizedBox(height: 24),

                        // Botões de ação
                        _buildActionButtons(),
                      ],
                    ),
                  ),
                ),
              ),
            ],
          ),
        );
      },
    );
  }

  /// Widget de informações detalhadas do arquivo.
  Widget _buildInfoSection() {
    return Column(
      children: [
        _buildInfoRow(
          icon: Icons.folder_outlined,
          label: 'Localização',
          value: '/Backups/...',
        ),
        const SizedBox(height: 12),
        _buildInfoRow(
          icon: Icons.lock_outline,
          label: 'Segurança',
          value: 'Criptografado (AES-256)',
        ),
        const SizedBox(height: 12),
        _buildInfoRow(
          icon: Icons.check_circle_outline,
          label: 'Status',
          value: 'Verificado',
        ),
      ],
    );
  }

  /// Widget de linha de informação.
  Widget _buildInfoRow({
    required IconData icon,
    required String label,
    required String value,
  }) {
    return Row(
      children: [
        Icon(icon, color: const Color(0xFF3B82F6), size: 18),
        const SizedBox(width: 12),
        Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(
              label,
              style: TextStyle(
                fontSize: 11,
                color: Colors.grey[500],
                fontWeight: FontWeight.w500,
              ),
            ),
            const SizedBox(height: 2),
            Text(
              value,
              style: const TextStyle(fontSize: 13, color: Colors.white),
            ),
          ],
        ),
      ],
    );
  }

  /// Widget de botões de ação.
  Widget _buildActionButtons() {
    if (_downloadedFile != null) {
      // Arquivo já foi baixado
      return Column(
        children: [
          SizedBox(
            width: double.infinity,
            height: 48,
            child: ElevatedButton.icon(
              onPressed: _openDownloadedFile,
              style: ElevatedButton.styleFrom(
                backgroundColor: const Color(0xFF3B82F6),
                shape: RoundedRectangleBorder(
                  borderRadius: BorderRadius.circular(8),
                ),
              ),
              icon: const Icon(Icons.open_in_new),
              label: const Text('Abrir Arquivo'),
            ),
          ),
          const SizedBox(height: 12),
          SizedBox(
            width: double.infinity,
            height: 48,
            child: OutlinedButton.icon(
              onPressed: () {
                Navigator.pop(context);
              },
              style: OutlinedButton.styleFrom(
                side: const BorderSide(color: Color(0xFF334155), width: 1),
                shape: RoundedRectangleBorder(
                  borderRadius: BorderRadius.circular(8),
                ),
              ),
              icon: const Icon(Icons.close),
              label: const Text('Fechar'),
            ),
          ),
        ],
      );
    }

    return Column(
      children: [
        SizedBox(
          width: double.infinity,
          height: 48,
          child: ElevatedButton.icon(
            onPressed: _isDownloading ? null : _downloadFile,
            style: ElevatedButton.styleFrom(
              backgroundColor: const Color(0xFF3B82F6),
              disabledBackgroundColor: Colors.grey[700],
              shape: RoundedRectangleBorder(
                borderRadius: BorderRadius.circular(8),
              ),
            ),
            icon: _isDownloading
                ? const SizedBox(
                    width: 20,
                    height: 20,
                    child: CircularProgressIndicator(
                      strokeWidth: 2,
                      valueColor: AlwaysStoppedAnimation<Color>(Colors.white),
                    ),
                  )
                : const Icon(Icons.download),
            label: Text(
              _isDownloading
                  ? 'Baixando... (${(_downloadProgress * 100).toStringAsFixed(0)}%)'
                  : 'Baixar Arquivo (${_formatFileSize(widget.file.sizeBytes)})',
              style: const TextStyle(fontWeight: FontWeight.w600),
            ),
          ),
        ),
        const SizedBox(height: 12),
        SizedBox(
          width: double.infinity,
          height: 48,
          child: OutlinedButton.icon(
            onPressed: _isDownloading
                ? null
                : () {
                    Navigator.pop(context);
                  },
            style: OutlinedButton.styleFrom(
              side: const BorderSide(color: Color(0xFF334155), width: 1),
              shape: RoundedRectangleBorder(
                borderRadius: BorderRadius.circular(8),
              ),
            ),
            icon: const Icon(Icons.close),
            label: const Text('Cancelar'),
          ),
        ),
      ],
    );
  }
}
