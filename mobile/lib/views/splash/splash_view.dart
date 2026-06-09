import 'package:flutter/material.dart';
import '../../core/constants/app_constants.dart';
import '../../core/themes/app_theme.dart';

/// [SplashView] - Tela de Splash inicial do aplicativo.
///
/// Esta é a primeira tela exibida quando o app é aberto. Ela tem como objetivos:
/// 1. Exibir a marca do Keeply enquanto o app carrega recursos
/// 2. Verificar se há usuário autenticado
/// 3. Redirecionar para a tela apropriada (Login ou BackupList)
///
/// Tempo de exibição:
/// - Configurado em AppConstants.splashDurationMillis (padrão: 2000ms = 2 segundos)
///
/// Fluxo:
/// ```
/// App Inicia → SplashView → (delay) → Verifica Auth → Redireciona
/// ```
///
/// Uso no roteamento:
/// ```dart
/// MaterialApp(
///   initialRoute: AppConstants.routeSplash,
///   routes: {
///     AppConstants.routeSplash: (_) => const SplashView(),
///   },
/// )
/// ```
class SplashView extends StatefulWidget {
  /// Construtor da SplashView.
  ///
  /// Usa const porque a tela não recebe parâmetros externos,
  /// permitindo otimizações do Flutter.
  const SplashView({super.key});

  @override
  State<SplashView> createState() => _SplashViewState();
}

/// [_SplashViewState] - Estado da tela de Splash.
///
/// Contém a lógica e os dados que podem mudar durante a exibição da Splash.
/// Usamos StatefulWidget porque precisamos:
/// - Aguardar um tempo (delay)
/// - Navegar para outra tela após o delay
class _SplashViewState extends State<SplashView>
    with SingleTickerProviderStateMixin {
  /// Controller da animação de fade (transparência).
  ///
  /// Usado para criar um efeito suave de aparecimento/desaparecimento.
  /// O vsync (this) garante que a animação seja sincronizada com o display.
  late AnimationController _animationController;

  /// Animação de opacidade (0.0 a 1.0).
  ///
  /// Controla a transparência do conteúdo durante o fade-in.
  late Animation<double> _fadeAnimation;

  /// Inicializa as animações quando o widget é criado.
  ///
  /// Este método é chamado uma vez quando o widget é inserido na árvore.
  @override
  void initState() {
    super.initState();

    // Configura o controller da animação
    _animationController = AnimationController(
      // Duração total da animação: 1 segundo
      duration: const Duration(milliseconds: 1000),
      // Garante que a animação comece transparente (0.0)
      value: 0.0,
      // vsync: sincroniza com o display para melhor performance
      vsync: this,
    );

    // Cria a animação de fade
    // CurvedAnimation permite usar curvas de easing (aceleração/desaceleração)
    _fadeAnimation = CurvedAnimation(
      parent: _animationController,
      // easeIn: começa devagar e acelera
      curve: Curves.easeIn,
    );

    // Inicia a animação
    _animationController.forward();

    // Agenda a navegação após o tempo configurado
    _agendarNavegacao();
  }

  /// Agenda a navegação para outra tela após o delay configurado.
  ///
  /// Usa Future.delayed para aguardar o tempo especificado nas constantes.
  /// A navegação usa pushReplacement para remover a Splash da pilha de navegação
  /// (não é possível voltar para a Splash com o botão back).
  Future<void> _agendarNavegacao() async {
    // Aguarda o tempo configurado (ex: 2 segundos)
    await Future.delayed(
      Duration(milliseconds: AppConstants.splashDurationMillis),
    );

    // Verifica se o widget ainda está montado (não foi descartado)
    if (!mounted) return;

    // Navega para a tela de Login
    // Em um app real, aqui você verificaria a autenticação:
    // final isLoggedIn = await AuthService.isLoggedIn();
    // if (isLoggedIn) {
    //   Navigator.pushReplacementNamed(context, AppConstants.routeBackupList);
    // } else {
    //   Navigator.pushReplacementNamed(context, AppConstants.routeLogin);
    // }
    Navigator.pushReplacementNamed(context, AppConstants.routeHome);
  }

  /// Libera recursos quando o widget é removido da árvore.
  ///
  /// Importante descartar o AnimationController para evitar memory leaks.
  @override
  void dispose() {
    // Descarta o controller e para a animação
    _animationController.dispose();
    super.dispose();
  }

  /// Build da interface da Splash.
  ///
  /// Retorna a árvore de widgets que compõe a tela de Splash.
  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: Container(
        decoration: const BoxDecoration(
          gradient: LinearGradient(
            begin: Alignment.topLeft,
            end: Alignment.bottomRight,
            colors: [
              AppTheme.primaryColor,
              Color(0xFF7C3AED),
            ],
          ),
        ),
        child: FadeTransition(
          opacity: _fadeAnimation,
          child: SafeArea(
            child: Center(
              child: Column(
                mainAxisAlignment: MainAxisAlignment.center,
                children: [
                  Container(
                    padding: const EdgeInsets.all(24),
                    decoration: BoxDecoration(
                      color: Colors.white.withValues(alpha: 0.15),
                      shape: BoxShape.circle,
                    ),
                    child: const Icon(
                      Icons.backup_rounded,
                      size: 80,
                      color: Colors.white,
                    ),
                  ),
                  const SizedBox(height: 32),
                  Text(
                    AppConstants.appName,
                    style: const TextStyle(
                      fontSize: 42,
                      fontWeight: FontWeight.bold,
                      color: Colors.white,
                      letterSpacing: 1.2,
                    ),
                  ),
                  const SizedBox(height: 8),
                  Padding(
                    padding: const EdgeInsets.symmetric(horizontal: 40),
                    child: Text(
                      AppConstants.appSlogan,
                      style: TextStyle(
                        fontSize: 16,
                        color: Colors.white.withValues(alpha: 0.85),
                      ),
                      textAlign: TextAlign.center,
                    ),
                  ),
                  const SizedBox(height: 48),
                  const SizedBox(
                    width: 32,
                    height: 32,
                    child: CircularProgressIndicator(
                      valueColor: AlwaysStoppedAnimation<Color>(Colors.white),
                      strokeWidth: 3,
                    ),
                  ),
                ],
              ),
            ),
          ),
        ),
      ),
    );
  }
}
