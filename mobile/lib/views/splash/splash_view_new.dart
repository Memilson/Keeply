import 'package:flutter/material.dart';
import '../../services/biometric_security_service.dart';
import '../../services/secure_storage_service.dart';
import '../pairing/qr_pairing_view.dart';
import '../security/security_question_view.dart';
import '../files/files_list_view.dart';

/// [SplashView] - Nova tela de Splash com fluxo de autenticação segura.
///
/// Responsabilidades:
/// 1. Exibir logo/branding do Keeply
/// 2. Verificar estado de autenticação/pareamento
/// 3. Orquestrar fluxo de segurança:
///    - Se pareado + biometria disponível → Pedir biometria
///    - Se biometria falhar/indisponível → Pedir perguntas de segurança
///    - Se não pareado → Exibir QR code de pareamento
/// 4. Redirecionar para tela apropriada após sucesso
///
/// Fluxo de decisão (na ordem):
/// ```
/// Iniciar App
///   ↓
/// Splash (2s com animação)
///   ↓
/// Verificar se pareado [SecureStorageService.isFullyConfigured()]
///   ├─ Se NÃO: → QRPairingView (pareamento com QR code)
///   └─ Se SIM:
///      ├─ Tem biometria? [BiometricSecurityService]
///      │  ├─ SIM: → Tentar biometria
///      │  │         ├─ Sucesso → FilesListView
///      │  │         └─ Falha → SecurityQuestionView (fallback)
///      │  │                     ├─ Acertou → FilesListView
///      │  │                     └─ Errou → Bloquear/Tentar novamente
///      │  └─ NÃO: → SecurityQuestionView (perguntas diretas)
/// ```
///
/// Segurança:
/// - Biometria: Integrada com Keychain/Keystore do SO
/// - Perguntas: Fallback seguro quando biometria indisponível
/// - Tokens: Nunca expostos em logs
/// - Falhas: Registradas para auditoria
///
/// Uso:
/// ```dart
/// MaterialApp(
///   initialRoute: AppConstants.routeSplash,
///   routes: {
///     AppConstants.routeSplash: (_) => const SplashView(),
///   },
/// )
/// ```
class SplashView extends StatefulWidget {
  const SplashView({super.key});

  @override
  State<SplashView> createState() => _SplashViewState();
}

/// [_SplashViewState] - Estado da tela de Splash com animações e lógica.
class _SplashViewState extends State<SplashView>
    with SingleTickerProviderStateMixin {
  /// Controller para animação de fade-in do logo.
  late AnimationController _logoController;

  /// Animação de opacidade (0 → 1).
  late Animation<double> _logoFade;

  /// Status de carregamento exibido ao usuário.
  String _loadingStatus = 'Inicializando segurança...';

  /// Flag de erro (para exibir mensagens).
  bool _hasError = false;
  String _errorMessage = '';

  @override
  void initState() {
    super.initState();
    _setupAnimations();
    _initializeApp();
  }

  /// Configura animações de entrada.
  void _setupAnimations() {
    _logoController = AnimationController(
      duration: const Duration(milliseconds: 1200),
      vsync: this,
    );

    _logoFade = CurvedAnimation(
      parent: _logoController,
      curve: Curves.easeInOut,
    );

    _logoController.forward();
  }

  /// Orquestra o fluxo completo de inicialização e autenticação.
  ///
  /// Fluxo:
  /// 1. Aguarda 2 segundos (splash visual)
  /// 2. Verifica se dispositivo está pareado
  /// 3. Se não pareado → vai para QRPairingView
  /// 4. Se pareado → tenta biometria
  /// 5. Se biometria indisponível/falha → perguntas de segurança
  /// 6. Em caso de sucesso → FilesListView
  Future<void> _initializeApp() async {
    try {
      // Aguarda splash visual (2 segundos)
      await Future.delayed(const Duration(seconds: 2));

      if (!mounted) return;

      setState(() {
        _loadingStatus = 'Verificando autenticação...';
      });

      // Verificar se dispositivo está pareado
      final secureStorage = SecureStorageService();
      final isPaired = await secureStorage.isPaired();

      if (!mounted) return;

      if (!isPaired) {
        // Dispositivo não pareado → exibir QR code
        print('Dispositivo não pareado. Exibindo QR pairing...');
        if (mounted) {
          Navigator.of(context).pushReplacementNamed('/pairing');
        }
        return;
      }

      // Dispositivo pareado → verificar autenticação
      setState(() {
        _loadingStatus = 'Verificando biometria...';
      });

      final bioSecurity = BiometricSecurityService();
      final canUseBio = await bioSecurity.canAuthenticateWithBiometrics();

      if (!mounted) return;

      if (canUseBio) {
        // Tentar autenticação biométrica
        print('Biometria disponível. Pedindo autenticação...');
        await _authenticateWithBiometric(bioSecurity);
      } else {
        // Biometria indisponível → usar perguntas de segurança
        print('Biometria indisponível. Usando perguntas de segurança...');
        if (mounted) {
          Navigator.of(context).pushReplacementNamed('/security-question');
        }
      }
    } catch (e) {
      print('Erro na inicialização: $e');
      if (mounted) {
        setState(() {
          _hasError = true;
          _errorMessage = 'Erro ao inicializar: $e';
        });
      }
    }
  }

  /// Tenta autenticar com biometria. Se falhar, mostra perguntas de segurança.
  ///
  /// Fluxo:
  /// 1. Tentar biometria com mensagem customizada
  /// 2. Se sucesso → ir para FilesListView
  /// 3. Se falha/cancel/erro → exibir SecurityQuestionView (fallback)
  /// 4. Se perguntas também falharem → bloquear acesso
  Future<void> _authenticateWithBiometric(
    BiometricSecurityService bioSecurity,
  ) async {
    try {
      setState(() {
        _loadingStatus = 'Autentique com biometria...';
      });

      final isAuthenticated = await bioSecurity.authenticateWithBiometrics(
        reason: 'Autentique-se para acessar seus arquivos no Keeply',
      );

      if (!mounted) return;

      if (isAuthenticated) {
        // Biometria bem-sucedida → ir para arquivos
        print('Autenticação biométrica bem-sucedida!');
        setState(() {
          _loadingStatus = 'Acessando arquivos...';
        });

        await Future.delayed(const Duration(milliseconds: 500));

        if (mounted) {
          Navigator.of(context).pushReplacementNamed('/files');
        }
      } else {
        // Biometria falhou/cancelada → usar fallback de perguntas
        print('Biometria falhou/cancelada. Exibindo perguntas de segurança...');

        if (mounted) {
          Navigator.of(context).pushReplacementNamed('/security-question');
        }
      }
    } catch (e) {
      print('Erro na autenticação biométrica: $e');

      if (mounted) {
        setState(() {
          _hasError = true;
          _errorMessage = 'Erro na autenticação: $e';
        });
      }
    }
  }

  @override
  void dispose() {
    _logoController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: Container(
        decoration: const BoxDecoration(
          gradient: LinearGradient(
            begin: Alignment.topLeft,
            end: Alignment.bottomRight,
            colors: [
              Color(0xFF0F172A), // Dark blue
              Color(0xFF1E293B), // Slightly lighter blue
            ],
          ),
        ),
        child: Center(
          child: _hasError ? _buildErrorWidget() : _buildLoadingWidget(),
        ),
      ),
    );
  }

  /// Widget de carregamento com animação.
  Widget _buildLoadingWidget() {
    return Column(
      mainAxisAlignment: MainAxisAlignment.center,
      children: [
        // Logo com fade-in
        FadeTransition(
          opacity: _logoFade,
          child: Container(
            width: 120,
            height: 120,
            decoration: BoxDecoration(
              shape: BoxShape.circle,
              border: Border.all(
                color: const Color(0xFF3B82F6), // Keeply blue
                width: 3,
              ),
            ),
            child: const Center(
              child: Text(
                'K',
                style: TextStyle(
                  fontSize: 64,
                  fontWeight: FontWeight.bold,
                  color: Color(0xFF3B82F6),
                  fontFamily: 'Roboto',
                ),
              ),
            ),
          ),
        ),

        const SizedBox(height: 48),

        // Texto "Keeply"
        FadeTransition(
          opacity: _logoFade,
          child: const Text(
            'Keeply',
            style: TextStyle(
              fontSize: 32,
              fontWeight: FontWeight.bold,
              color: Colors.white,
              fontFamily: 'Roboto',
            ),
          ),
        ),

        const SizedBox(height: 12),

        // Subtítulo
        FadeTransition(
          opacity: _logoFade,
          child: Text(
            'Seus arquivos. Sua segurança.',
            style: TextStyle(
              fontSize: 14,
              color: Colors.grey[400],
              fontFamily: 'Roboto',
            ),
          ),
        ),

        const SizedBox(height: 60),

        // Indicador de progresso com status
        Column(
          children: [
            // Circular progress
            const SizedBox(
              width: 50,
              height: 50,
              child: CircularProgressIndicator(
                valueColor: AlwaysStoppedAnimation<Color>(Color(0xFF3B82F6)),
                strokeWidth: 3,
              ),
            ),

            const SizedBox(height: 20),

            // Texto de status
            Text(
              _loadingStatus,
              style: const TextStyle(
                fontSize: 14,
                color: Colors.white70,
                fontFamily: 'Roboto',
              ),
              textAlign: TextAlign.center,
            ),
          ],
        ),

        const SizedBox(height: 80),

        // Versão (rodapé)
        Text(
          'Versão 1.0.0',
          style: TextStyle(
            fontSize: 11,
            color: Colors.grey[600],
            fontFamily: 'Roboto',
          ),
        ),
      ],
    );
  }

  /// Widget de erro com botão de retry.
  Widget _buildErrorWidget() {
    return Column(
      mainAxisAlignment: MainAxisAlignment.center,
      children: [
        // Ícone de erro
        Container(
          width: 80,
          height: 80,
          decoration: BoxDecoration(
            shape: BoxShape.circle,
            border: Border.all(color: Colors.red, width: 2),
          ),
          child: const Center(
            child: Icon(Icons.error_outline, color: Colors.red, size: 48),
          ),
        ),

        const SizedBox(height: 24),

        // Mensagem de erro
        Padding(
          padding: const EdgeInsets.symmetric(horizontal: 32),
          child: Text(
            _errorMessage,
            style: const TextStyle(
              fontSize: 14,
              color: Colors.white,
              fontFamily: 'Roboto',
            ),
            textAlign: TextAlign.center,
          ),
        ),

        const SizedBox(height: 32),

        // Botão de retry
        ElevatedButton.icon(
          onPressed: () {
            setState(() {
              _hasError = false;
              _errorMessage = '';
              _loadingStatus = 'Reiniciando...';
            });
            _initializeApp();
          },
          style: ElevatedButton.styleFrom(
            backgroundColor: const Color(0xFF3B82F6),
            padding: const EdgeInsets.symmetric(horizontal: 32, vertical: 12),
            shape: RoundedRectangleBorder(
              borderRadius: BorderRadius.circular(8),
            ),
          ),
          icon: const Icon(Icons.refresh),
          label: const Text(
            'Tentar Novamente',
            style: TextStyle(fontSize: 14, fontWeight: FontWeight.w600),
          ),
        ),
      ],
    );
  }
}
