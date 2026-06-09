import 'package:flutter/material.dart';
import 'package:shared_preferences/shared_preferences.dart';
import '../../core/constants/app_constants.dart';
import '../../services/auth_service.dart';
import '../../services/permission_service.dart';

class SettingsView extends StatefulWidget {
  const SettingsView({super.key});
  @override
  State<SettingsView> createState() => _SettingsViewState();
}

class _SettingsViewState extends State<SettingsView> {
  bool _biometricsEnabled = false;
  bool _biometricAvailable = false;
  bool _storageGranted = false;
  bool _cameraGranted = false;
  final PermissionService _permissions = PermissionService();
  final AuthService _authService = AuthService();

  @override
  void initState() {
    super.initState();
    _checkPermissions();
    _loadSettings();
  }

  Future<void> _loadSettings() async {
    final prefs = await SharedPreferences.getInstance();
    final storedValue = prefs.getBool(AppConstants.storageKeyBiometricsEnabled);
    final supported = await _authService.canAuthenticateBiometrics();

    if (!mounted) return;
    setState(() {
      _biometricAvailable = supported;
      _biometricsEnabled = supported ? (storedValue ?? true) : false;
    });
  }

  Future<void> _saveBiometricPreference(bool enabled) async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setBool(AppConstants.storageKeyBiometricsEnabled, enabled);
    if (!mounted) return;
    setState(() {
      _biometricsEnabled = enabled;
    });
  }

  Future<void> _checkPermissions() async {
    final storage = await _permissions.hasStoragePermission();
    final camera = await _permissions.hasCameraPermission();
    if (mounted) {
      setState(() {
        _storageGranted = storage;
        _cameraGranted = camera;
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: const Color(0xFF0F172A),
      appBar: AppBar(
        title: const Text(
          'Configurações',
          style: TextStyle(
            fontWeight: FontWeight.w700,
            fontSize: 18,
            color: Colors.white,
          ),
        ),
        automaticallyImplyLeading: false,
        backgroundColor: const Color(0xFF1E293B),
        elevation: 0,
      ),
      body: ListView(
        padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 10),
        children: [
          // Permissões
          _buildSectionHeader('PERMISSÕES'),
          _buildCard(
            child: Column(
              children: [
                _buildPermissionTile(
                  icon: Icons.folder_shared,
                  color: const Color(0xFF06B6D4),
                  title: 'Armazenamento',
                  granted: _storageGranted,
                  onTap: () async {
                    if (!_storageGranted) {
                      final granted = await _permissions
                          .requestStoragePermission();
                      if (mounted) {
                        setState(() => _storageGranted = granted);
                        if (!granted) {
                          final perm = await _permissions
                              .isStoragePermanentlyDenied();
                          if (perm) _permissions.openSettings();
                        }
                      }
                    }
                  },
                ),
                const Divider(color: Color(0xFF334155), height: 1),
                _buildPermissionTile(
                  icon: Icons.camera_alt_outlined,
                  color: const Color(0xFF8B5CF6),
                  title: 'Câmera (QR Scanner)',
                  granted: _cameraGranted,
                  onTap: () async {
                    if (!_cameraGranted) {
                      final granted = await _permissions
                          .requestCameraPermission();
                      if (mounted) setState(() => _cameraGranted = granted);
                    }
                  },
                ),
              ],
            ),
          ),
          const SizedBox(height: 24),

          _buildSectionHeader('CONEXÃO'),
          _buildCard(
            child: Column(
              children: [
                ListTile(
                  leading: _iconCircle(
                    Icons.cloud_done_rounded,
                    const Color(0xFF22C55E),
                  ),
                  title: const Text(
                    'Modo de Conexão',
                    style: TextStyle(color: Colors.white),
                  ),
                  subtitle: const Text(
                    'Online sempre que pareado. Se não houver conexão, o app aguardará até que a rede retorne.',
                    style: TextStyle(color: Color(0xFF64748B), fontSize: 12),
                  ),
                ),
                const Divider(color: Color(0xFF334155), height: 1),
                ListTile(
                  leading: _iconCircle(Icons.link, const Color(0xFF3B82F6)),
                  title: const Text(
                    'Backend',
                    style: TextStyle(color: Colors.white),
                  ),
                  subtitle: Text(
                    AppConstants.backendBaseUrl,
                    style: const TextStyle(
                      color: Color(0xFF64748B),
                      fontSize: 12,
                    ),
                  ),
                  trailing: const Icon(
                    Icons.arrow_forward_ios,
                    size: 14,
                    color: Color(0xFF64748B),
                  ),
                  onTap: () {},
                ),
                const Divider(color: Color(0xFF334155), height: 1),
                ListTile(
                  leading: _iconCircle(
                    Icons.cloud_download,
                    const Color(0xFF22C55E),
                  ),
                  title: const Text(
                    'Arquivos Remotos',
                    style: TextStyle(color: Colors.white),
                  ),
                  subtitle: const Text(
                    'Verificar e baixar arquivos do backend',
                    style: TextStyle(color: Color(0xFF64748B), fontSize: 12),
                  ),
                  trailing: const Icon(
                    Icons.arrow_forward_ios,
                    size: 14,
                    color: Color(0xFF64748B),
                  ),
                  onTap: () =>
                      Navigator.of(context).pushNamed(AppConstants.routeFiles),
                ),
              ],
            ),
          ),
          const SizedBox(height: 24),

          _buildSectionHeader('ARMAZENAMENTO'),
          _buildCard(
            child: Column(
              children: [
                ListTile(
                  leading: _iconCircle(
                    Icons.folder_shared,
                    const Color(0xFF06B6D4),
                  ),
                  title: const Text(
                    'Local de Scan',
                    style: TextStyle(color: Colors.white),
                  ),
                  subtitle: const Text(
                    '/storage/emulated/0',
                    style: TextStyle(color: Color(0xFF64748B), fontSize: 12),
                  ),
                  trailing: const Icon(
                    Icons.arrow_forward_ios,
                    size: 14,
                    color: Color(0xFF64748B),
                  ),
                  onTap: () {},
                ),
              ],
            ),
          ),
          const SizedBox(height: 24),

          _buildSectionHeader('SEGURANÇA'),
          _buildCard(
            child: Column(
              children: [
                SwitchListTile(
                  title: const Text(
                    'Autenticação Biométrica',
                    style: TextStyle(color: Colors.white),
                  ),
                  subtitle: Text(
                    _biometricAvailable
                        ? 'Exigir digital ao abrir os arquivos remotos'
                        : 'Biometria não disponível neste dispositivo',
                    style: const TextStyle(
                      color: Color(0xFF64748B),
                      fontSize: 12,
                    ),
                  ),
                  secondary: _iconCircle(
                    Icons.fingerprint,
                    const Color(0xFF22C55E),
                  ),
                  value: _biometricsEnabled,
                  onChanged: _biometricAvailable
                      ? (v) => _saveBiometricPreference(v)
                      : null,
                  activeTrackColor: const Color(0xFF3B82F6),
                ),
              ],
            ),
          ),
          const SizedBox(height: 48),

          // Info
          Center(
            child: Text(
              '${AppConstants.appName} v1.0.0',
              style: const TextStyle(color: Color(0xFF64748B), fontSize: 12),
            ),
          ),
          const SizedBox(height: 4),
          const Center(
            child: Text(
              'O aplicativo funciona online sempre que possível. Use o QR Code para parear com o backend.',
              textAlign: TextAlign.center,
              style: TextStyle(color: Color(0xFF475569), fontSize: 11),
            ),
          ),
          const SizedBox(height: 20),
        ],
      ),
    );
  }

  Widget _buildPermissionTile({
    required IconData icon,
    required Color color,
    required String title,
    required bool granted,
    required VoidCallback onTap,
  }) {
    return ListTile(
      leading: _iconCircle(icon, color),
      title: Text(title, style: const TextStyle(color: Colors.white)),
      subtitle: Text(
        granted ? 'Concedida' : 'Não concedida',
        style: TextStyle(
          color: granted ? const Color(0xFF22C55E) : const Color(0xFFEF4444),
          fontSize: 12,
        ),
      ),
      trailing: Container(
        padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
        decoration: BoxDecoration(
          color: granted
              ? const Color(0xFF22C55E).withValues(alpha: 0.15)
              : const Color(0xFFEF4444).withValues(alpha: 0.15),
          borderRadius: BorderRadius.circular(8),
          border: Border.all(
            color: granted
                ? const Color(0xFF22C55E).withValues(alpha: 0.3)
                : const Color(0xFFEF4444).withValues(alpha: 0.3),
          ),
        ),
        child: Row(
          mainAxisSize: MainAxisSize.min,
          children: [
            Icon(
              granted ? Icons.check_circle : Icons.error_outline,
              color: granted
                  ? const Color(0xFF22C55E)
                  : const Color(0xFFEF4444),
              size: 14,
            ),
            const SizedBox(width: 4),
            Text(
              granted ? 'OK' : 'Habilitar',
              style: TextStyle(
                color: granted
                    ? const Color(0xFF22C55E)
                    : const Color(0xFFEF4444),
                fontSize: 11,
                fontWeight: FontWeight.w600,
              ),
            ),
          ],
        ),
      ),
      onTap: granted ? null : onTap,
    );
  }

  Widget _iconCircle(IconData icon, Color color) {
    return Container(
      padding: const EdgeInsets.all(8),
      decoration: BoxDecoration(
        color: color.withValues(alpha: 0.15),
        shape: BoxShape.circle,
      ),
      child: Icon(icon, color: color, size: 20),
    );
  }

  Widget _buildSectionHeader(String title) {
    return Padding(
      padding: const EdgeInsets.only(left: 8, bottom: 8, top: 8),
      child: Text(
        title,
        style: const TextStyle(
          fontSize: 12,
          fontWeight: FontWeight.bold,
          color: Color(0xFF64748B),
          letterSpacing: 1.2,
        ),
      ),
    );
  }

  Widget _buildCard({required Widget child}) {
    return Container(
      decoration: BoxDecoration(
        color: const Color(0xFF1E293B),
        borderRadius: BorderRadius.circular(16),
        border: Border.all(color: const Color(0xFF334155)),
      ),
      child: ClipRRect(borderRadius: BorderRadius.circular(16), child: child),
    );
  }
}
