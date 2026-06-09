import 'package:flutter/material.dart';
import 'dart:ui';
import 'package:path_provider/path_provider.dart';
import '../../controllers/ws_controller.dart';
import '../../models/ws_config.dart';
import '../dashboard/dashboard_screen.dart';
import '../backups/scan_history_view.dart';
import '../pairing/qr_pairing_view.dart';
import '../settings/settings_view.dart';

class HomeView extends StatefulWidget {
  const HomeView({super.key});
  @override
  State<HomeView> createState() => _HomeViewState();
}

class _HomeViewState extends State<HomeView> {
  int _currentIndex = 0;
  final WsController _ws = WsController();

  @override
  void initState() {
    super.initState();
    _initializeAgent();
  }

  /// Inicializa o agente com identidade local.
  /// O app permanece online-first e só usa fallback offline quando perder conexão.
  Future<void> _initializeAgent() async {
    final dir = await getApplicationDocumentsDirectory();
    final config = WsConfig(
      url: '',
      deviceName: 'keeply-mobile',
      hostName: 'android-device',
      osName: 'android',
      identityDir: dir.path,
    );

    // Gera/carrega identidade local para permitir pareamento e status.
    final identity = await _ws.identityController.loadIdentity();
    config.agentId = identity.deviceId;

    _ws.initLocalState(config, identity);
  }

  @override
  void dispose() {
    _ws.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final pages = [
      DashboardScreen(ws: _ws),
      ScanHistoryView(),
      QrPairingView(ws: _ws),
      const SettingsView(),
    ];

    return Scaffold(
      extendBody: true,
      body: pages[_currentIndex],
      bottomNavigationBar: ClipRRect(
        child: BackdropFilter(
          filter: ImageFilter.blur(sigmaX: 10, sigmaY: 10),
          child: Container(
            decoration: BoxDecoration(
              color: const Color(0xFF1E293B).withValues(alpha: 0.95),
              border: Border(
                top: BorderSide(color: Colors.white.withValues(alpha: 0.05)),
              ),
            ),
            child: SafeArea(
              top: false,
              child: Padding(
                padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 8),
                child: Row(
                  mainAxisAlignment: MainAxisAlignment.spaceAround,
                  children: [
                    _buildNavItem(
                      0,
                      Icons.dashboard_rounded,
                      'Dashboard',
                      const Color(0xFF3B82F6),
                    ),
                    _buildNavItem(
                      1,
                      Icons.history_rounded,
                      'Histórico',
                      const Color(0xFF8B5CF6),
                    ),
                    _buildNavItem(
                      2,
                      Icons.qr_code_2_rounded,
                      'Parear',
                      const Color(0xFF06B6D4),
                    ),
                    _buildNavItem(
                      3,
                      Icons.settings_outlined,
                      'Config',
                      const Color(0xFF64748B),
                    ),
                  ],
                ),
              ),
            ),
          ),
        ),
      ),
    );
  }

  Widget _buildNavItem(
    int index,
    IconData icon,
    String label,
    Color selectedColor,
  ) {
    final isSelected = _currentIndex == index;
    final color = isSelected ? selectedColor : const Color(0xFF64748B);
    return GestureDetector(
      onTap: () => setState(() => _currentIndex = index),
      behavior: HitTestBehavior.opaque,
      child: Container(
        padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 4),
        decoration: isSelected
            ? BoxDecoration(
                color: selectedColor.withValues(alpha: 0.1),
                borderRadius: BorderRadius.circular(12),
              )
            : null,
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Icon(icon, color: color, size: 22),
            const SizedBox(height: 4),
            Text(
              label,
              style: TextStyle(
                color: color,
                fontSize: 11,
                fontWeight: isSelected ? FontWeight.w600 : FontWeight.normal,
              ),
            ),
          ],
        ),
      ),
    );
  }
}
