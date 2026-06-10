import 'dart:io';
import 'package:flutter/material.dart';
import 'core/constants/app_constants.dart';
import 'views/splash/splash_view_new.dart';
import 'views/pairing/login_view.dart';
import 'views/security/security_question_view.dart';
import 'views/files/files_list_view.dart';
import 'views/main_shell.dart';
import 'package:provider/provider.dart';
import 'controllers/auth_controller.dart';
import 'controllers/files_controller.dart';
import 'controllers/settings_controller.dart';

class MyHttpOverrides extends HttpOverrides {
  @override
  HttpClient createHttpClient(SecurityContext? context) {
    return super.createHttpClient(context)
      ..badCertificateCallback = (X509Certificate cert, String host, int port) => true;
  }
}

void main() {
  HttpOverrides.global = MyHttpOverrides();
  WidgetsFlutterBinding.ensureInitialized();
  
  runApp(
    MultiProvider(
      providers: [
        ChangeNotifierProvider(create: (_) => AuthController()),
        ChangeNotifierProvider(create: (_) => FilesController()),
        ChangeNotifierProvider(create: (_) => SettingsController()),
      ],
      child: const KeeplyApp(),
    ),
  );
}
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
        AppConstants.routeSplash: (context) => const SplashView(),
        AppConstants.routePairing: (context) => const LoginView(),
        '/security-question': (context) => const SecurityQuestionView(),
        AppConstants.routeFiles: (context) => const FilesListView(),
        '/main': (context) => MainShell(),
      },
      onGenerateRoute: (settings) {
        return MaterialPageRoute(builder: (context) => const SplashView());
      },
    );
  }
}
