import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:file_picker/file_picker.dart';
import '../../services/secure_storage_service.dart';
class SettingsView extends StatefulWidget {
  const SettingsView({super.key});
  @override
  State<SettingsView> createState() => _SettingsViewState();
}
class _SettingsViewState extends State<SettingsView> {
  String _userName = 'Kalleb';
  String _userEmail = 'kalleb@keeply.com';
  String _downloadDir = '/storage/emulated/0/Download';
  final SecureStorageService _secureStorage = SecureStorageService();
  @override
  void initState() {
    super.initState();
    _loadUserProfile();
  }
  Future<void> _loadUserProfile() async {
    final name = await _secureStorage.getUserName() ?? 'Kalleb';
    final email = await _secureStorage.getUserEmail() ?? 'kalleb@keeply.com';
    setState(() {
      _userName = name;
      _userEmail = email;
    });
    final dir = await _secureStorage.getDownloadDir();
    if (dir != null && dir.isNotEmpty && mounted) {
      setState(() => _downloadDir = dir);
    }
  }
  String _getInitials(String name) {
    if (name.isEmpty) return 'U';
    final parts = name.trim().split(' ');
    if (parts.length > 1) {
      return (parts[0][0] + parts[1][0]).toUpperCase();
    }
    return parts[0][0].toUpperCase();
  }
  void _handleDisconnectAccount() async {
    final secureStorage = SecureStorageService();
    await secureStorage.clearAll();
    if (mounted) {
      Navigator.of(context).pushNamedAndRemoveUntil('/splash', (route) => false);
    }
  }
  void _handleChangeDownloadFolder() async {
    final result = await FilePicker.platform.getDirectoryPath(
      dialogTitle: 'Selecione a pasta para salvar downloads',
    );
    if (result != null && result.isNotEmpty) {
      await _secureStorage.saveDownloadDir(result);
      if (mounted) {
        setState(() => _downloadDir = result);
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text('Pasta alterada para: $result'),
            backgroundColor: const Color(0xFF10B981),
          ),
        );
      }
    }
  }
  void _handleExitApp() {
    SystemNavigator.pop();
  }
  Widget _buildSectionTitle(String title) {
    return Padding(
      padding: const EdgeInsets.only(left: 8, bottom: 8, top: 16),
      child: Text(
        title.toUpperCase(),
        style: const TextStyle(
          fontSize: 12,
          fontWeight: FontWeight.w600,
          color: Color(0xFF64748B), 
          letterSpacing: 1.2,
        ),
      ),
    );
  }
  Widget _buildDarkCard({required Widget child}) {
    return Container(
      decoration: BoxDecoration(
        color: const Color(0xFF0D0C22), 
        borderRadius: BorderRadius.circular(16),
        border: Border.all(
          color: const Color(0xFF7B61FF).withValues(alpha: 0.15),
          width: 1,
        ),
      ),
      child: ClipRRect(
        borderRadius: BorderRadius.circular(16),
        child: child,
      ),
    );
  }
  Widget _buildDivider() {
    return Divider(
      height: 1,
      thickness: 1,
      color: const Color(0xFF7B61FF).withValues(alpha: 0.15),
      indent: 56, 
    );
  }
  Widget _buildAccountSection() {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        _buildSectionTitle('Conta'),
        _buildDarkCard(
          child: Column(
            children: [
              ListTile(
                contentPadding: const EdgeInsets.symmetric(
                  horizontal: 16,
                  vertical: 8,
                ),
                leading: CircleAvatar(
                  backgroundColor: const Color(0xFF7B61FF),
                  foregroundColor: Colors.white,
                  child: Text(_getInitials(_userName), style: const TextStyle(fontWeight: FontWeight.bold)),
                ),
                title: Text(
                  _userName,
                  style: const TextStyle(
                    color: Colors.white,
                    fontWeight: FontWeight.bold,
                    fontSize: 16,
                  ),
                ),
                subtitle: Padding(
                  padding: const EdgeInsets.only(top: 4.0),
                  child: Text(
                    _userEmail,
                    style: TextStyle(color: Colors.grey[400], fontSize: 13),
                  ),
                ),
                trailing: Container(
                  padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
                  decoration: BoxDecoration(
                    color: const Color(0xFF10B981).withValues(alpha: 0.1), 
                    borderRadius: BorderRadius.circular(12),
                    border: Border.all(
                      color: const Color(0xFF10B981).withValues(alpha: 0.3),
                    ),
                  ),
                  child: const Text(
                    'ATIVO',
                    style: TextStyle(
                      color: Color(0xFF10B981),
                      fontSize: 10,
                      fontWeight: FontWeight.bold,
                      letterSpacing: 0.5,
                    ),
                  ),
                ),
              ),
              _buildDivider(),
              ListTile(
                onTap: _handleDisconnectAccount,
                leading: Container(
                  padding: const EdgeInsets.all(8),
                  decoration: BoxDecoration(
                    color: const Color(0xFFF59E0B).withValues(alpha: 0.1), 
                    borderRadius: BorderRadius.circular(8),
                  ),
                  child: const Icon(
                    Icons.sync_disabled,
                    color: Color(0xFFF59E0B),
                  ),
                ),
                title: const Text(
                  'Desconectar conta',
                  style: TextStyle(color: Colors.white, fontSize: 15),
                ),
                subtitle: Text(
                  'Remover pareamento deste dispositivo',
                  style: TextStyle(color: Colors.grey[400], fontSize: 13),
                ),
                trailing: Icon(Icons.chevron_right, color: Colors.grey[500]),
              ),
            ],
          ),
        ),
      ],
    );
  }
  Widget _buildStorageSection() {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        _buildSectionTitle('Armazenamento'),
        _buildDarkCard(
          child: ListTile(
            onTap: _handleChangeDownloadFolder,
            leading: Container(
              padding: const EdgeInsets.all(8),
              decoration: BoxDecoration(
                color: const Color(0xFFEAB308).withValues(alpha: 0.1), 
                borderRadius: BorderRadius.circular(8),
              ),
              child: const Icon(
                Icons.folder,
                color: Color(0xFFEAB308),
              ),
            ),
            title: const Text(
              'Pasta para Salvar',
              style: TextStyle(color: Colors.white, fontSize: 15),
            ),
            subtitle: Text(
              _downloadDir,
              overflow: TextOverflow.ellipsis,
              style: TextStyle(color: Colors.grey[400], fontSize: 13),
            ),
            trailing: Icon(Icons.chevron_right, color: Colors.grey[500]),
          ),
        ),
      ],
    );
  }
  Widget _buildSessionSection() {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        _buildSectionTitle('Sessão'),
        _buildDarkCard(
          child: ListTile(
            onTap: _handleExitApp,
            leading: Container(
              padding: const EdgeInsets.all(8),
              decoration: BoxDecoration(
                color: const Color(0xFFEF4444).withValues(alpha: 0.1), 
                borderRadius: BorderRadius.circular(8),
              ),
              child: const Icon(
                Icons.exit_to_app,
                color: Color(0xFFEF4444),
              ),
            ),
            title: const Text(
              'Sair do aplicativo',
              style: TextStyle(
                color: Color(0xFFEF4444),
                fontSize: 15,
                fontWeight: FontWeight.w500,
              ),
            ),
            subtitle: Text(
              'Encerrar sessão neste dispositivo',
              style: TextStyle(color: Colors.grey[400], fontSize: 13),
            ),
            trailing: Icon(Icons.chevron_right, color: Colors.grey[500]),
          ),
        ),
      ],
    );
  }
  Widget _buildFooter() {
    return Padding(
      padding: const EdgeInsets.only(top: 32, bottom: 24),
      child: Center(
        child: Text(
          'Keeply v1.0.0',
          style: TextStyle(
            color: Colors.grey[600]?.withValues(alpha: 0.5),
            fontSize: 12,
            fontWeight: FontWeight.w500,
            letterSpacing: 0.5,
          ),
        ),
      ),
    );
  }
  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: const Color(0xFF08071A), 
      appBar: AppBar(
        automaticallyImplyLeading: false,
        backgroundColor: const Color(0xFF08071A),
        elevation: 0,
        centerTitle: false,
        title: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const Text(
              'Configurações',
              style: TextStyle(
                fontSize: 24,
                fontWeight: FontWeight.bold,
                color: Colors.white,
              ),
            ),
            const SizedBox(height: 4),
            Text(
              'Segurança, conta e armazenamento',
              style: TextStyle(
                fontSize: 13,
                color: Colors.grey[400],
                fontWeight: FontWeight.normal,
              ),
            ),
          ],
        ),
      ),
      body: ListView(
        padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
        physics: const BouncingScrollPhysics(),
        children: [
          _buildAccountSection(),
          const SizedBox(height: 16),
          _buildStorageSection(),
          const SizedBox(height: 16),
          _buildSessionSection(),
          _buildFooter(),
        ],
      ),
    );
  }
}
