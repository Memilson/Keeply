import 'dart:io';
import 'package:flutter/material.dart';
import '../../controllers/ws_controller.dart';
import '../../services/media_scanner_service.dart';
import '../../services/permission_service.dart';
import '../widgets/schedule_card.dart';

class DashboardScreen extends StatefulWidget {
  final WsController ws;
  const DashboardScreen({super.key, required this.ws});
  @override
  State<DashboardScreen> createState() => _DashboardScreenState();
}

class _DashboardScreenState extends State<DashboardScreen> {
  final MediaScannerService _scanner = MediaScannerService();
  final PermissionService _permissions = PermissionService();
  int _localFileCount = 0;
  int _localTotalBytes = 0;
  int _imageCount = 0;
  int _videoCount = 0;
  int _docCount = 0;
  bool _isScanning = false;
  String _scanStatus = 'Pronto para escanear';

  @override
  void initState() {
    super.initState();
    widget.ws.addListener(_refresh);
    // Auto-scan ao iniciar se estiver offline
    if (widget.ws.offlineMode) {
      _runLocalScan();
    }
  }

  @override
  void dispose() { widget.ws.removeListener(_refresh); super.dispose(); }
  void _refresh() => setState(() {});

  Future<void> _runLocalScan() async {
    // Verificar permissões antes de escanear
    final hasPermission = await _permissions.hasStoragePermission();
    if (!hasPermission) {
      final granted = await _permissions.requestStoragePermission();
      if (!granted) {
        if (!mounted) return;
        setState(() {
          _scanStatus = 'Permissão de armazenamento necessária';
        });
        widget.ws.addLog('[scan] Permissão de armazenamento negada');
        return;
      }
    }

    setState(() {
      _isScanning = true;
      _scanStatus = 'Escaneando...';
    });

    widget.ws.addLog('[scan] Iniciando scan local...');

    // Diretórios padrão do Android para escanear
    final dirsToScan = [
      '/storage/emulated/0/DCIM',
      '/storage/emulated/0/Pictures',
      '/storage/emulated/0/Movies',
      '/storage/emulated/0/Download',
      '/storage/emulated/0/Documents',
    ];

    int totalFiles = 0;
    int totalBytes = 0;
    int images = 0;
    int videos = 0;
    int docs = 0;

    for (final dirPath in dirsToScan) {
      final dir = Directory(dirPath);
      if (!await dir.exists()) continue;

      setState(() => _scanStatus = 'Escaneando ${dirPath.split('/').last}...');

      try {
        final files = await _scanner.scanDirectory(dirPath);
        for (final file in files) {
          totalFiles++;
          try {
            totalBytes += await file.length();
          } catch (_) {}

          final ext = file.path.split('.').last.toLowerCase();
          if (MediaScannerService.imageExtensions.contains(ext)) {
            images++;
          } else if (MediaScannerService.videoExtensions.contains(ext)) {
            videos++;
          } else if (MediaScannerService.documentExtensions.contains(ext)) {
            docs++;
          }
        }
      } catch (e) {
        widget.ws.addLog('[scan] Erro em $dirPath: $e');
      }
    }

    if (!mounted) return;

    setState(() {
      _localFileCount = totalFiles;
      _localTotalBytes = totalBytes;
      _imageCount = images;
      _videoCount = videos;
      _docCount = docs;
      _isScanning = false;
      _scanStatus = totalFiles > 0
          ? '$totalFiles arquivos encontrados'
          : 'Nenhum arquivo encontrado';
    });

    widget.ws.addLog('[scan] Concluído: $totalFiles arquivos, ${(_localTotalBytes / 1048576).toStringAsFixed(1)} MB');
  }

  String _formatSize(int bytes) {
    if (bytes < 1024) return '$bytes B';
    if (bytes < 1048576) return '${(bytes / 1024).toStringAsFixed(1)} KB';
    if (bytes < 1073741824) return '${(bytes / 1048576).toStringAsFixed(1)} MB';
    return '${(bytes / 1073741824).toStringAsFixed(1)} GB';
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: const Color(0xFF0F172A),
      appBar: AppBar(
        title: const Text('Keeply Dashboard', style: TextStyle(fontWeight: FontWeight.w700, fontSize: 18, color: Colors.white)),
        backgroundColor: const Color(0xFF1E293B), elevation: 0,
        actions: [
          // Mostra modo: Offline ou Online
          _buildModeBadge(),
          const SizedBox(width: 12),
        ],
      ),
      body: SingleChildScrollView(
        padding: const EdgeInsets.all(16),
        child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
          // Banner de modo offline
          if (widget.ws.offlineMode) _buildOfflineBanner(),
          _buildAgentCard(),
          const SizedBox(height: 12),
          _buildLocalScanCard(),
          const SizedBox(height: 12),
          _buildMediaBreakdownCard(),
          const SizedBox(height: 12),
          _buildScopeCard(),
          const SizedBox(height: 12),
          if (!widget.ws.offlineMode) ...[
            _buildProgressCard(),
            const SizedBox(height: 12),
            ScheduleCard(ws: widget.ws),
            const SizedBox(height: 12),
            _buildSnapshotsCard(),
          ],
          _buildLogCard(),
        ]),
      ),
    );
  }

  Widget _buildModeBadge() {
    final isOffline = widget.ws.offlineMode;
    return Container(
      margin: const EdgeInsets.symmetric(vertical: 12),
      padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
      decoration: BoxDecoration(
        color: isOffline
            ? const Color(0xFFF59E0B).withValues(alpha: 0.15)
            : const Color(0xFF22C55E).withValues(alpha: 0.15),
        borderRadius: BorderRadius.circular(12),
        border: Border.all(
          color: isOffline
              ? const Color(0xFFF59E0B).withValues(alpha: 0.4)
              : const Color(0xFF22C55E).withValues(alpha: 0.4),
        ),
      ),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          Icon(
            isOffline ? Icons.wifi_off_rounded : Icons.wifi_rounded,
            color: isOffline ? const Color(0xFFF59E0B) : const Color(0xFF22C55E),
            size: 14,
          ),
          const SizedBox(width: 6),
          Text(
            isOffline ? 'Offline' : 'Online',
            style: TextStyle(
              color: isOffline ? const Color(0xFFF59E0B) : const Color(0xFF22C55E),
              fontSize: 11, fontWeight: FontWeight.w600,
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildOfflineBanner() {
    return Container(
      width: double.infinity,
      margin: const EdgeInsets.only(bottom: 16),
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        gradient: LinearGradient(
          colors: [
            const Color(0xFFF59E0B).withValues(alpha: 0.15),
            const Color(0xFF0F172A),
          ],
          begin: Alignment.topLeft,
          end: Alignment.bottomRight,
        ),
        borderRadius: BorderRadius.circular(16),
        border: Border.all(color: const Color(0xFFF59E0B).withValues(alpha: 0.3)),
      ),
      child: Row(
        children: [
          Container(
            padding: const EdgeInsets.all(10),
            decoration: BoxDecoration(
              color: const Color(0xFFF59E0B).withValues(alpha: 0.2),
              borderRadius: BorderRadius.circular(12),
            ),
            child: const Icon(Icons.cloud_off_rounded, color: Color(0xFFF59E0B), size: 24),
          ),
          const SizedBox(width: 14),
          const Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text('Modo Local',
                    style: TextStyle(color: Color(0xFFF59E0B), fontWeight: FontWeight.w700, fontSize: 15)),
                SizedBox(height: 2),
                Text('Funcionando sem conexão ao servidor.\nScan e visualização de arquivos ativos.',
                    style: TextStyle(color: Color(0xFF94A3B8), fontSize: 12)),
              ],
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildAgentCard() {
    final config = widget.ws.config;
    return _card(icon: Icons.devices, iconColor: const Color(0xFF3B82F6), title: 'Agente', child: Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        _infoRow('Device ID', config.agentId.isNotEmpty ? '${config.agentId.substring(0, 12)}...' : '—'),
        _infoRow('Hostname', config.hostName),
        _infoRow('OS', config.osName),
        _infoRow('Modo', widget.ws.offlineMode ? 'Offline (Local)' : (widget.ws.connected ? 'Online' : 'Desconectado')),
      ],
    ));
  }

  Widget _buildLocalScanCard() {
    return _card(
      icon: Icons.radar_rounded,
      iconColor: const Color(0xFF22C55E),
      title: 'Scan Local',
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          // Status
          Row(
            children: [
              Container(
                width: 8, height: 8,
                decoration: BoxDecoration(
                  color: _isScanning ? const Color(0xFFF59E0B) : const Color(0xFF22C55E),
                  shape: BoxShape.circle,
                ),
              ),
              const SizedBox(width: 8),
              Expanded(
                child: Text(
                  _scanStatus,
                  style: const TextStyle(color: Color(0xFFCBD5E1), fontSize: 13),
                ),
              ),
              if (_isScanning)
                const SizedBox(
                  width: 16, height: 16,
                  child: CircularProgressIndicator(
                    color: Color(0xFFF59E0B), strokeWidth: 2,
                  ),
                ),
            ],
          ),
          const SizedBox(height: 12),

          // Stats grid
          Row(
            children: [
              _buildMiniStat('Arquivos', _localFileCount.toString(), const Color(0xFF3B82F6)),
              const SizedBox(width: 8),
              _buildMiniStat('Tamanho', _formatSize(_localTotalBytes), const Color(0xFF22C55E)),
            ],
          ),
          const SizedBox(height: 12),

          // Scan button
          SizedBox(
            width: double.infinity,
            child: ElevatedButton.icon(
              onPressed: _isScanning ? null : _runLocalScan,
              icon: Icon(
                _isScanning ? Icons.hourglass_top : Icons.play_arrow_rounded,
                size: 18,
              ),
              label: Text(_isScanning ? 'Escaneando...' : 'Escanear Agora'),
              style: ElevatedButton.styleFrom(
                backgroundColor: const Color(0xFF3B82F6),
                foregroundColor: Colors.white,
                disabledBackgroundColor: const Color(0xFF334155),
                disabledForegroundColor: const Color(0xFF64748B),
                padding: const EdgeInsets.symmetric(vertical: 12),
                shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(10)),
              ),
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildMiniStat(String label, String value, Color color) {
    return Expanded(
      child: Container(
        padding: const EdgeInsets.all(12),
        decoration: BoxDecoration(
          color: color.withValues(alpha: 0.08),
          borderRadius: BorderRadius.circular(10),
          border: Border.all(color: color.withValues(alpha: 0.2)),
        ),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(value, style: TextStyle(color: color, fontSize: 18, fontWeight: FontWeight.w700)),
            const SizedBox(height: 2),
            Text(label, style: const TextStyle(color: Color(0xFF64748B), fontSize: 11)),
          ],
        ),
      ),
    );
  }

  Widget _buildMediaBreakdownCard() {
    return _card(
      icon: Icons.pie_chart_rounded,
      iconColor: const Color(0xFF8B5CF6),
      title: 'Distribuição de Mídia',
      child: Column(
        children: [
          _mediaTypeRow(Icons.image_outlined, 'Imagens', _imageCount, const Color(0xFF3B82F6)),
          const SizedBox(height: 8),
          _mediaTypeRow(Icons.videocam_outlined, 'Vídeos', _videoCount, const Color(0xFF8B5CF6)),
          const SizedBox(height: 8),
          _mediaTypeRow(Icons.description_outlined, 'Documentos', _docCount, const Color(0xFFEF4444)),
        ],
      ),
    );
  }

  Widget _mediaTypeRow(IconData icon, String label, int count, Color color) {
    final total = _localFileCount > 0 ? _localFileCount : 1;
    final percent = (count / total * 100).toStringAsFixed(0);
    return Row(
      children: [
        Container(
          width: 32, height: 32,
          decoration: BoxDecoration(
            color: color.withValues(alpha: 0.1),
            borderRadius: BorderRadius.circular(8),
          ),
          child: Icon(icon, color: color, size: 16),
        ),
        const SizedBox(width: 10),
        Expanded(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(label, style: const TextStyle(color: Color(0xFFE2E8F0), fontSize: 12)),
              const SizedBox(height: 4),
              ClipRRect(
                borderRadius: BorderRadius.circular(3),
                child: LinearProgressIndicator(
                  value: count / total,
                  backgroundColor: const Color(0xFF334155),
                  valueColor: AlwaysStoppedAnimation<Color>(color),
                  minHeight: 4,
                ),
              ),
            ],
          ),
        ),
        const SizedBox(width: 10),
        Text('$count ($percent%)', style: TextStyle(color: color, fontSize: 12, fontWeight: FontWeight.w600)),
      ],
    );
  }

  Widget _buildProgressCard() {
    final progress = widget.ws.currentProgress;
    if (progress == null) {
      return _card(icon: Icons.cloud_upload, iconColor: const Color(0xFF22C55E), title: 'Backup',
        child: const Text('Nenhum backup em andamento', style: TextStyle(color: Color(0xFF94A3B8), fontSize: 13)));
    }
    return _card(icon: Icons.cloud_upload, iconColor: const Color(0xFFF59E0B), title: 'Backup em Progresso', child: Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        _infoRow('Fase', progress.phase),
        _infoRow('Arquivos', '${progress.filesCompleted}/${progress.filesQueued}'),
        _infoRow('Adicionados', '${progress.stats.added}'),
        _infoRow('Reutilizados', '${progress.stats.reused}'),
        _infoRow('Bytes lidos', '${(progress.stats.bytesRead / 1048576).toStringAsFixed(1)} MB'),
        const SizedBox(height: 8),
        ClipRRect(borderRadius: BorderRadius.circular(4), child: LinearProgressIndicator(
          value: progress.progressPercent, backgroundColor: const Color(0xFF334155),
          valueColor: const AlwaysStoppedAnimation<Color>(Color(0xFF3B82F6)), minHeight: 6,
        )),
      ],
    ));
  }

  Widget _buildScopeCard() {
    final scope = widget.ws.appState.scanScope;
    return _card(icon: Icons.folder_open, iconColor: const Color(0xFFEC4899), title: 'Escopo de Scan', child: Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        _infoRow('ID', scope.id),
        _infoRow('Label', scope.label),
        _infoRow('Path', scope.resolvedPath.isNotEmpty ? scope.resolvedPath : '/storage/emulated/0'),
      ],
    ));
  }

  Widget _buildSnapshotsCard() {
    final snaps = widget.ws.snapshots;
    return _card(icon: Icons.history, iconColor: const Color(0xFF8B5CF6), title: 'Execucoes Recentes (${snaps.length})',
      child: snaps.isEmpty
        ? const Text('Nenhum snapshot', style: TextStyle(color: Color(0xFF94A3B8), fontSize: 13))
        : Column(children: snaps.take(10).map((s) => Padding(
            padding: const EdgeInsets.only(bottom: 6),
            child: Row(children: [
              Container(width: 8, height: 8, decoration: const BoxDecoration(color: Color(0xFF22C55E), shape: BoxShape.circle)),
              const SizedBox(width: 8),
              Expanded(child: Text('${s.label.isNotEmpty ? s.label : "snap-${s.id}"} — ${s.createdAt}', style: const TextStyle(color: Color(0xFFCBD5E1), fontSize: 12), overflow: TextOverflow.ellipsis)),
              Text('${s.fileCount} arq', style: const TextStyle(color: Color(0xFF64748B), fontSize: 11)),
            ]),
          )).toList()),
    );
  }

  Widget _buildLogCard() {
    final logs = widget.ws.log;
    if (logs.isEmpty) return const SizedBox.shrink();

    return Container(
      margin: const EdgeInsets.only(top: 12),
      width: double.infinity,
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: const Color(0xFF1E293B),
        borderRadius: BorderRadius.circular(12),
        border: Border.all(color: const Color(0xFF334155)),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(children: [
            const Icon(Icons.terminal_rounded, color: Color(0xFF64748B), size: 16),
            const SizedBox(width: 8),
            const Text('Log de Atividade', style: TextStyle(color: Colors.white, fontWeight: FontWeight.w600, fontSize: 14)),
            const Spacer(),
            Text('${logs.length}', style: const TextStyle(color: Color(0xFF64748B), fontSize: 11)),
          ]),
          const SizedBox(height: 12),
          Container(
            constraints: const BoxConstraints(maxHeight: 150),
            child: ListView.builder(
              reverse: true,
              shrinkWrap: true,
              itemCount: logs.length,
              itemBuilder: (context, index) {
                final entry = logs[logs.length - 1 - index];
                return Padding(
                  padding: const EdgeInsets.only(bottom: 2),
                  child: Text(
                    entry,
                    style: const TextStyle(
                      color: Color(0xFF94A3B8),
                      fontSize: 10,
                      fontFamily: 'monospace',
                    ),
                  ),
                );
              },
            ),
          ),
        ],
      ),
    );
  }

  Widget _card({required IconData icon, required Color iconColor, required String title, required Widget child}) {
    return Container(
      width: double.infinity, padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(color: const Color(0xFF1E293B), borderRadius: BorderRadius.circular(12), border: Border.all(color: const Color(0xFF334155))),
      child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
        Row(children: [Icon(icon, color: iconColor, size: 18), const SizedBox(width: 8), Text(title, style: const TextStyle(color: Colors.white, fontWeight: FontWeight.w600, fontSize: 14))]),
        const SizedBox(height: 12), child,
      ]),
    );
  }

  Widget _infoRow(String label, String value) {
    return Padding(padding: const EdgeInsets.only(bottom: 4), child: Row(children: [
      SizedBox(width: 100, child: Text(label, style: const TextStyle(color: Color(0xFF64748B), fontSize: 12))),
      Expanded(child: Text(value, style: const TextStyle(color: Color(0xFFE2E8F0), fontSize: 12), overflow: TextOverflow.ellipsis)),
    ]));
  }
}
