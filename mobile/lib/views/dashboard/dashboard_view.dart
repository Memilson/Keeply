import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import '../../controllers/dashboard_controller.dart';
import '../../widgets/keeply_mark.dart';

class DashboardView extends StatefulWidget {
  const DashboardView({super.key});
  @override
  State<DashboardView> createState() => _DashboardViewState();
}

class _DashboardViewState extends State<DashboardView> {
  static const Color _azure = Color(0xFF007FFF);
  static const Color _bg = Color(0xFF08071A);
  static const Color _surface = Color(0xFF0D0C22);

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) {
      context.read<DashboardController>().fetchMetrics();
    });
  }

  String _formatBytes(int bytes) {
    if (bytes <= 0) return '0 B';
    const units = ['B', 'KB', 'MB', 'GB'];
    double value = bytes.toDouble();
    int unit = 0;
    while (value > 1024 && unit < units.length - 1) {
      value /= 1024;
      unit++;
    }
    return '${value.toStringAsFixed(1)} ${units[unit]}';
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: _bg,
      appBar: AppBar(
        automaticallyImplyLeading: false,
        backgroundColor: _bg,
        elevation: 0,
        title: const Row(
          children: [
            KeeplyMark(size: 28),
            SizedBox(width: 10),
            Text(
              'Dashboard',
              style: TextStyle(
                color: Colors.white,
                fontSize: 22,
                fontWeight: FontWeight.w800,
              ),
            ),
          ],
        ),
        actions: [
          IconButton(
            icon: const Icon(Icons.refresh, color: Colors.white70),
            onPressed: () => context.read<DashboardController>().fetchMetrics(),
          ),
        ],
      ),
      body: Consumer<DashboardController>(
        builder: (context, controller, _) {
          return RefreshIndicator(
            color: _azure,
            onRefresh: controller.fetchMetrics,
            child: ListView(
              padding: const EdgeInsets.all(16),
              children: [
                if (controller.errorMessage != null)
                  _statusBox(controller.errorMessage!),
                GridView.count(
                  crossAxisCount: 2,
                  shrinkWrap: true,
                  physics: const NeverScrollableScrollPhysics(),
                  mainAxisSpacing: 10,
                  crossAxisSpacing: 10,
                  childAspectRatio: 1.25,
                  children: [
                    _metricCard(
                      'Backups',
                      '${controller.successfulBackups}',
                      Icons.inventory_2,
                    ),
                    _metricCard(
                      'Arquivos',
                      '${controller.totalFiles}',
                      Icons.insert_drive_file,
                    ),
                    _metricCard(
                      'Storage',
                      _formatBytes(controller.totalStorageBytes),
                      Icons.storage,
                    ),
                    _metricCard(
                      'Downloads',
                      '${controller.downloadedFiles}',
                      Icons.download,
                    ),
                  ],
                ),
                const SizedBox(height: 14),
                _infoBlock(
                  'Como usar no mobile',
                  'Consulte backups, navegue por pastas dos snapshots e baixe arquivos. Backup e restauração ficam no Keeply Agente.',
                  Icons.phone_android,
                ),
                const SizedBox(height: 10),
                _infoBlock(
                  'Restauração',
                  'Escolha a máquina e o snapshot na web. O Keeply Agente executa a restauração em uma pasta do dispositivo.',
                  Icons.settings_backup_restore,
                ),
                if (controller.isLoading)
                  const Padding(
                    padding: EdgeInsets.only(top: 24),
                    child: Center(
                      child: CircularProgressIndicator(color: _azure),
                    ),
                  ),
              ],
            ),
          );
        },
      ),
    );
  }

  Widget _metricCard(String label, String value, IconData icon) {
    return Container(
      padding: const EdgeInsets.all(14),
      decoration: BoxDecoration(
        color: _surface,
        borderRadius: BorderRadius.circular(8),
        border: Border.all(color: _azure.withValues(alpha: 0.20)),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        mainAxisAlignment: MainAxisAlignment.spaceBetween,
        children: [
          Icon(icon, color: _azure, size: 24),
          Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(
                value,
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
                style: const TextStyle(
                  color: Colors.white,
                  fontSize: 22,
                  fontWeight: FontWeight.w800,
                ),
              ),
              const SizedBox(height: 2),
              Text(
                label,
                style: const TextStyle(color: Color(0xFF94A3B8), fontSize: 12),
              ),
            ],
          ),
        ],
      ),
    );
  }

  Widget _infoBlock(String title, String text, IconData icon) {
    return Container(
      padding: const EdgeInsets.all(14),
      decoration: BoxDecoration(
        color: _surface,
        borderRadius: BorderRadius.circular(8),
        border: Border.all(color: Colors.white.withValues(alpha: 0.08)),
      ),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Icon(icon, color: _azure, size: 22),
          const SizedBox(width: 12),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  title,
                  style: const TextStyle(
                    color: Colors.white,
                    fontSize: 14,
                    fontWeight: FontWeight.w700,
                  ),
                ),
                const SizedBox(height: 4),
                Text(
                  text,
                  style: const TextStyle(
                    color: Color(0xFFCBD5E1),
                    fontSize: 12,
                    height: 1.35,
                  ),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }

  Widget _statusBox(String text) {
    return Container(
      margin: const EdgeInsets.only(bottom: 12),
      padding: const EdgeInsets.all(12),
      decoration: BoxDecoration(
        color: const Color(0xFF92400E).withValues(alpha: 0.20),
        borderRadius: BorderRadius.circular(8),
        border: Border.all(
          color: const Color(0xFFF59E0B).withValues(alpha: 0.25),
        ),
      ),
      child: Text(
        text,
        style: const TextStyle(color: Color(0xFFFCD34D), fontSize: 12),
      ),
    );
  }
}
