import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'ai/ai_assistant_view.dart';
import 'files/files_list_view.dart';
import 'settings/settings_view.dart';

class MainShell extends StatefulWidget {
  const MainShell({super.key});
  @override
  State<MainShell> createState() => _MainShellState();
}

class _MainShellState extends State<MainShell> {
  int _currentIndex = 0;
  final List<Widget> _screens = [
    const FilesListView(),
    const AiAssistantView(),
    const SettingsView(),
  ];
  Future<void> _handlePop(bool didPop, Object? result) async {
    if (didPop) return;
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        backgroundColor: const Color(0xFF0D0C22),
        title: const Text(
          'Sair do Keeply',
          style: TextStyle(color: Colors.white),
        ),
        content: const Text(
          'Deseja realmente sair do aplicativo?',
          style: TextStyle(color: Color(0xFFCBD5E1)),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.of(context).pop(false),
            child: const Text(
              'Cancelar',
              style: TextStyle(color: Color(0xFF94A3B8)),
            ),
          ),
          TextButton(
            onPressed: () => Navigator.of(context).pop(true),
            child: const Text(
              'Sair',
              style: TextStyle(color: Color(0xFFEF4444)),
            ),
          ),
        ],
      ),
    );
    if (confirmed == true) {
      SystemNavigator.pop();
    }
  }

  @override
  Widget build(BuildContext context) {
    return PopScope(
      canPop: false,
      onPopInvokedWithResult: _handlePop,
      child: Scaffold(
        backgroundColor: const Color(0xFF08071A),
        body: IndexedStack(index: _currentIndex, children: _screens),
        bottomNavigationBar: Container(
          decoration: BoxDecoration(
            border: Border(
              top: BorderSide(
                color: const Color(0xFF7B61FF).withValues(alpha: 0.3),
                width: 1,
              ),
            ),
          ),
          child: BottomNavigationBar(
            currentIndex: _currentIndex,
            onTap: (index) {
              setState(() {
                _currentIndex = index;
              });
            },
            backgroundColor: const Color(0xFF08071A),
            selectedItemColor: const Color(0xFF7B61FF),
            unselectedItemColor: const Color(0xFF4A5A7A),
            showUnselectedLabels: true,
            type: BottomNavigationBarType.fixed,
            items: const [
              BottomNavigationBarItem(
                icon: Icon(Icons.history),
                label: 'Histórico',
              ),
              BottomNavigationBarItem(
                icon: Icon(Icons.auto_awesome),
                label: 'I.A',
              ),
              BottomNavigationBarItem(
                icon: Icon(Icons.settings),
                label: 'Configurações',
              ),
            ],
          ),
        ),
      ),
    );
  }
}
