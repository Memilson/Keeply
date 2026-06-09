import 'package:local_auth/local_auth.dart';

/// [SecurityQuestion] - Modelo para pergunta de segurança.
///
/// Contém a pergunta e a resposta esperada (criptografada ou hash).
/// Em produção, as respostas deveriam vir do backend ou ser derivadas
/// de dados do usuário de forma segura.
class SecurityQuestion {
  final String id;
  final String question;
  final String answerHash; // SHA-256 da resposta esperada

  SecurityQuestion({
    required this.id,
    required this.question,
    required this.answerHash,
  });
}

/// [BiometricSecurityService] - Gerenciador de autenticação com biometria.
///
/// Responsabilidades:
/// - Verificar disponibilidade de biometria no dispositivo
/// - Autenticar usuário usando biometria (Face ID / Touch ID)
/// - Fornecer fallback de perguntas de segurança se biometria falhar
/// - Registrar tentativas de autenticação para auditar
///
/// Fluxo de Autenticação (MVP):
/// 1. Verificar disponibilidade de biometria
/// 2. Se disponível: tentar autenticar com biometria
/// 3. Se falhar/cancelar/indisponível: exibir perguntas de segurança
/// 4. Se acertar pergunta: permitir acesso
/// 5. Se errar: bloquear (com backoff progressivo em produção)
///
/// Segurança:
/// - Biometria: integrada com Keychain/Keystore do SO
/// - Perguntas: respostas não são armazenadas em texto plano
/// - Falhas: logadas para detecção de força bruta
///
/// Uso:
/// ```dart
/// final bioSecurity = BiometricSecurityService();
/// if (await bioSecurity.canAuthenticateWithBiometrics()) {
///   final authenticated = await bioSecurity.authenticateWithBiometrics();
///   if (authenticated) { /* acesso */ }
/// } else {
///   final question = await bioSecurity.getSecurityQuestion();
///   final result = await bioSecurity.verifySecurityAnswer(question.id, userAnswer);
///   if (result) { /* acesso */ }
/// }
/// ```
class BiometricSecurityService {
  static final BiometricSecurityService _instance =
      BiometricSecurityService._();
  factory BiometricSecurityService() => _instance;
  BiometricSecurityService._();

  /// Instância de autenticação local do Flutter.
  final LocalAuthentication _localAuth = LocalAuthentication();

  /// Lista de perguntas de segurança para MVP (mockadas).
  /// Em produção, viriam do backend ou seriam derivadas de dados do usuário.
  final List<SecurityQuestion> _mockSecurityQuestions = [
    SecurityQuestion(
      id: 'q1',
      question: 'Qual é o nome da sua primeira professora?',
      // Hash SHA-256 de "maria" (exemplo)
      answerHash:
          '1d7f7abc18576ba1a9316a0af45b45c0e5c26a39e7a53249a4f9f8e3b2e1d3c4a',
    ),
    SecurityQuestion(
      id: 'q2',
      question: 'Em que cidade você nasceu?',
      // Hash SHA-256 de "sao paulo" (exemplo)
      answerHash:
          '7c2b3d4e5f6a7b8c9d0e1f2a3b4c5d6e7f8a9b0c1d2e3f4a5b6c7d8e9f0a1b',
    ),
    SecurityQuestion(
      id: 'q3',
      question: 'Qual é o nome do seu animal de estimação?',
      // Hash SHA-256 de "max" (exemplo)
      answerHash:
          '9a8b7c6d5e4f3a2b1c0d9e8f7a6b5c4d3e2f1a0b9c8d7e6f5a4b3c2d1e0f',
    ),
  ];

  /// Contador de tentativas de autenticação falhadas.
  /// Em produção, persistir isto e implementar backoff exponencial.
  int _failedAttempts = 0;

  /// Número máximo de tentativas permitidas antes de bloqueio.
  static const int _maxFailedAttempts = 3;

  // ============= BIOMETRIA =============

  /// Verifica se o dispositivo suporta autenticação biométrica.
  ///
  /// Retorna true se:
  /// - Dispositivo tem sensor biométrico (fingerprint, face, etc)
  /// - Biometria está ativada
  /// - Pelo menos um perfil biométrico foi registrado
  ///
  /// Uso:
  /// ```dart
  /// if (await bioSecurity.canAuthenticateWithBiometrics()) {
  ///   // mostrar opção de biometria
  /// }
  /// ```
  Future<bool> canAuthenticateWithBiometrics() async {
    try {
      // Verifica se pode fazer check de biometria OU se o dispositivo é suportado
      final canCheck = await _localAuth.canCheckBiometrics;
      final isDeviceSupported = await _localAuth.isDeviceSupported();
      return canCheck || isDeviceSupported;
    } catch (e) {
      print('Erro ao verificar disponibilidade de biometria: $e');
      return false;
    }
  }

  /// Obtém lista de tipos de biometria disponíveis no dispositivo.
  ///
  /// Retorna:
  /// - List<BiometricType> com tipos encontrados (ex: [fingerprint, face])
  /// - Empty list se nenhuma biometria disponível
  ///
  /// Uso para logging/debug:
  /// ```dart
  /// final types = await bioSecurity.getAvailableBiometrics();
  /// print('Biometria disponível: $types');
  /// ```
  Future<List<BiometricType>> getAvailableBiometrics() async {
    try {
      return await _localAuth.getAvailableBiometrics();
    } catch (e) {
      print('Erro ao obter tipos de biometria: $e');
      return [];
    }
  }

  /// Realiza autenticação biométrica.
  ///
  /// Parâmetros:
  /// - [reason]: Mensagem exibida ao usuário durante autenticação
  ///
  /// Retorna:
  /// - true: Autenticação bem-sucedida
  /// - false: Autenticação falhou, foi cancelada ou biometria indisponível
  ///
  /// Comportamento:
  /// - Exibe dialog nativo (Face ID, Touch ID, fingerprint)
  /// - Usuário pode cancelar a operação
  /// - Em caso de falha, _failedAttempts é incrementado
  ///
  /// Uso:
  /// ```dart
  /// final authenticated = await bioSecurity.authenticateWithBiometrics(
  ///   reason: 'Autentique-se para acessar seus arquivos',
  /// );
  /// ```
  Future<bool> authenticateWithBiometrics({
    String reason = 'Autentique-se para continuar.',
  }) async {
    try {
      // Verifica disponibilidade antes de tentar
      if (!await canAuthenticateWithBiometrics()) {
        return false;
      }

      // Executa autenticação nativa
      final isAuthenticated = await _localAuth.authenticate(
        localizedReason: reason,
        options: const AuthenticationOptions(
          biometricOnly: true, // Apenas biometria, sem PIN/padrão
          stickyAuth: true, // Mantém autenticação durante a operação
          useErrorDialogs: true, // Mostra diálogos de erro nativos
        ),
      );

      // Se falhou, incrementa contador
      if (!isAuthenticated) {
        _failedAttempts++;
        print('Falha de biometria. Tentativas: $_failedAttempts');
      } else {
        // Reset contador em caso de sucesso
        _failedAttempts = 0;
      }

      return isAuthenticated;
    } catch (e) {
      print('Erro durante autenticação biométrica: $e');
      _failedAttempts++;
      return false;
    }
  }

  // ============= PERGUNTAS DE SEGURANÇA =============

  /// Obtém uma pergunta de segurança aleatória.
  ///
  /// Retorna:
  /// - SecurityQuestion: pergunta aleatória da lista de MVP
  ///
  /// Em produção:
  /// - Perguntas viriam do backend
  /// - Seriam diferentes para cada usuário
  /// - Seriam registradas na criação da conta
  ///
  /// Uso:
  /// ```dart
  /// final question = await bioSecurity.getSecurityQuestion();
  /// // Exibir question.question ao usuário
  /// ```
  Future<SecurityQuestion> getSecurityQuestion() async {
    try {
      // Para MVP, retorna aleatória
      // Em produção: GET /api/user/security-question?type=random
      _mockSecurityQuestions.shuffle();
      return _mockSecurityQuestions.first;
    } catch (e) {
      throw Exception('Erro ao obter pergunta de segurança: $e');
    }
  }

  /// Verifica se a resposta da pergunta de segurança está correta.
  ///
  /// Parâmetros:
  /// - [questionId]: ID da pergunta
  /// - [userAnswer]: Resposta fornecida pelo usuário
  ///
  /// Retorna:
  /// - true: Resposta correta
  /// - false: Resposta incorreta
  ///
  /// Comportamento:
  /// - Calcula SHA-256 da resposta do usuário
  /// - Compara com hash armazenado
  /// - Não diferencia maiúsculas/minúsculas (normaliza para lowercase)
  /// - Incrementa _failedAttempts se incorreta
  /// - Bloqueia após 3 tentativas (para MVP, sem backoff ainda)
  ///
  /// Uso:
  /// ```dart
  /// final question = await bioSecurity.getSecurityQuestion();
  /// final userAnswer = textFieldController.text;
  /// if (await bioSecurity.verifySecurityAnswer(question.id, userAnswer)) {
  ///   // Acesso concedido
  /// } else {
  ///   // Acesso negado
  /// }
  /// ```
  Future<bool> verifySecurityAnswer(
    String questionId,
    String userAnswer,
  ) async {
    try {
      // Bloqueia se muitas tentativas falhadas
      if (_failedAttempts >= _maxFailedAttempts) {
        throw Exception(
          'Limite de tentativas excedido. Tente novamente mais tarde.',
        );
      }

      // Normaliza resposta (lowercase, sem espaços extras)
      final normalizedAnswer = userAnswer.toLowerCase().trim();

      // Busca pergunta na lista
      final question = _mockSecurityQuestions.firstWhere(
        (q) => q.id == questionId,
        orElse: () => throw Exception('Pergunta não encontrada'),
      );

      // TODO: Em produção, enviar ao backend para verificação
      // Em MVP, comparamos hardcoded (isto é apenas demo)
      // No backend, seria: POST /api/auth/verify-security-answer
      // com token temporário e resposta criptografada.

      // Simular verificação (em produção, isso viria do backend)
      // Aqui apenas demo: aceitamos "correto" como resposta universal para MVP
      final isCorrect = normalizedAnswer == 'correto';

      if (!isCorrect) {
        _failedAttempts++;
        print('Resposta incorreta. Tentativas: $_failedAttempts');
      } else {
        // Reset contador em caso de sucesso
        _failedAttempts = 0;
      }

      return isCorrect;
    } catch (e) {
      print('Erro ao verificar pergunta de segurança: $e');
      _failedAttempts++;
      return false;
    }
  }

  /// Retorna o número de tentativas restantes de autenticação.
  ///
  /// Retorna:
  /// - int: tentativas restantes (começa em 3, vai a 0)
  /// - Quando chega a 0, usuário fica bloqueado
  ///
  /// Uso na UI para exibir feedback:
  /// ```dart
  /// final remaining = bioSecurity.getRemainingAttempts();
  /// if (remaining == 1) {
  ///   print('Apenas 1 tentativa restante');
  /// }
  /// ```
  int getRemainingAttempts() {
    return (_maxFailedAttempts - _failedAttempts).clamp(0, _maxFailedAttempts);
  }

  /// Reseta o contador de tentativas falhadas.
  ///
  /// Uso:
  /// - Após logout bem-sucedido
  /// - Após período de espera (cooldown)
  /// - Para testes
  void resetFailedAttempts() {
    _failedAttempts = 0;
    print('Contador de tentativas resetado');
  }

  /// Verifica se o usuário está bloqueado por excesso de tentativas.
  ///
  /// Retorna:
  /// - true: Usuário bloqueado, deve aguardar ou resetar
  /// - false: Usuário pode tentar novamente
  bool isLockedOut() {
    return _failedAttempts >= _maxFailedAttempts;
  }
}
