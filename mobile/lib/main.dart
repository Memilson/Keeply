import 'package:flutter/material.dart';
import 'core/constants/app_constants.dart';
import 'views/splash/splash_view_new.dart';
import 'views/pairing/qr_pairing_view.dart';
import 'views/security/security_question_view.dart';
import 'views/files/files_list_view.dart';

void main() {
  WidgetsFlutterBinding.ensureInitialized();
  runApp(const KeeplyApp());
}

/// [KeeplyApp] - Aplicativo principal do Keeply.
///
/// Configuração:
/// - Tema escuro com Material 3
/// - Cores principais: #3B82F6 (azul), #06B6D4 (ciano)
/// - Rota inicial: /splash
///
/// Fluxo de Navegação:
/// /splash (verificação de pareamento)
///   ├─ Pareado + biometria OK → /files
///   ├─ Pareado + biometria falhou → /security-question
///   └─ Não pareado → /pairing
///
/// Rotas Registradas:
/// - /splash: SplashViewNew (orquestrador de autenticação)
/// - /pairing: QrPairingView (pareamento via QR)
/// - /security-question: SecurityQuestionView (fallback auth)
/// - /files: FilesListView (tela principal estilo OneDrive)
class KeeplyApp extends StatelessWidget {
  const KeeplyApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: AppConstants.appName,
      debugShowCheckedModeBanner: false,
      theme: ThemeData(
        brightness: Brightness.dark,
        scaffoldBackgroundColor: const Color(0xFF0F172A),
        colorScheme: const ColorScheme.dark(
          primary: Color(0xFF3B82F6),
          secondary: Color(0xFF06B6D4),
          surface: Color(0xFF1E293B),
        ),
        fontFamily: 'Roboto',
      ),
      initialRoute: AppConstants.routeSplash,
      routes: {
        AppConstants.routeSplash: (context) => const SplashViewNew(),
        AppConstants.routePairing: (context) => const QrPairingView(),
        '/security-question': (context) => const SecurityQuestionView(),
        AppConstants.routeFiles: (context) => const FilesListView(),
      },
      onGenerateRoute: (settings) {
        return MaterialPageRoute(builder: (context) => const SplashViewNew());
      },
    );
  }
}
