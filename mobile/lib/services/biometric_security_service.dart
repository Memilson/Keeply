import 'package:local_auth/local_auth.dart';
class SecurityQuestion {
  final String id;
  final String question;
  final String answerHash; 
  SecurityQuestion({
    required this.id,
    required this.question,
    required this.answerHash,
  });
}
class BiometricSecurityService {
  static final BiometricSecurityService _instance =
      BiometricSecurityService._();
  factory BiometricSecurityService() => _instance;
  BiometricSecurityService._();
  final LocalAuthentication _localAuth = LocalAuthentication();
  final List<SecurityQuestion> _mockSecurityQuestions = [
    SecurityQuestion(
      id: 'q1',
      question: 'Qual é o nome da sua primeira professora?',
      answerHash:
          '1d7f7abc18576ba1a9316a0af45b45c0e5c26a39e7a53249a4f9f8e3b2e1d3c4a',
    ),
    SecurityQuestion(
      id: 'q2',
      question: 'Em que cidade você nasceu?',
      answerHash:
          '7c2b3d4e5f6a7b8c9d0e1f2a3b4c5d6e7f8a9b0c1d2e3f4a5b6c7d8e9f0a1b',
    ),
    SecurityQuestion(
      id: 'q3',
      question: 'Qual é o nome do seu animal de estimação?',
      answerHash:
          '9a8b7c6d5e4f3a2b1c0d9e8f7a6b5c4d3e2f1a0b9c8d7e6f5a4b3c2d1e0f',
    ),
  ];
  int _failedAttempts = 0;
  static const int _maxFailedAttempts = 3;
  Future<bool> canAuthenticateWithBiometrics() async {
    try {
      final canCheck = await _localAuth.canCheckBiometrics;
      final isDeviceSupported = await _localAuth.isDeviceSupported();
      return canCheck || isDeviceSupported;
    } catch (e) {
      print('Erro ao verificar disponibilidade de biometria: $e');
      return false;
    }
  }
  Future<List<BiometricType>> getAvailableBiometrics() async {
    try {
      return await _localAuth.getAvailableBiometrics();
    } catch (e) {
      print('Erro ao obter tipos de biometria: $e');
      return [];
    }
  }
  Future<bool> authenticateWithBiometrics({
    String reason = 'Autentique-se para continuar.',
  }) async {
    try {
      if (!await canAuthenticateWithBiometrics()) {
        return false;
      }
      final isAuthenticated = await _localAuth.authenticate(
        localizedReason: reason,
        options: const AuthenticationOptions(
          biometricOnly: true, 
          stickyAuth: true, 
          useErrorDialogs: true, 
        ),
      );
      if (!isAuthenticated) {
        _failedAttempts++;
        print('Falha de biometria. Tentativas: $_failedAttempts');
      } else {
        _failedAttempts = 0;
      }
      return isAuthenticated;
    } catch (e) {
      print('Erro durante autenticação biométrica: $e');
      _failedAttempts++;
      return false;
    }
  }
  Future<SecurityQuestion> getSecurityQuestion() async {
    try {
      _mockSecurityQuestions.shuffle();
      return _mockSecurityQuestions.first;
    } catch (e) {
      throw Exception('Erro ao obter pergunta de segurança: $e');
    }
  }
  Future<bool> verifySecurityAnswer(
    String questionId,
    String userAnswer,
  ) async {
    try {
      if (_failedAttempts >= _maxFailedAttempts) {
        throw Exception(
          'Limite de tentativas excedido. Tente novamente mais tarde.',
        );
      }
      final normalizedAnswer = userAnswer.toLowerCase().trim();
      _mockSecurityQuestions.firstWhere(
        (q) => q.id == questionId,
        orElse: () => throw Exception('Pergunta não encontrada'),
      );
      final isCorrect = normalizedAnswer == 'correto';
      if (!isCorrect) {
        _failedAttempts++;
        print('Resposta incorreta. Tentativas: $_failedAttempts');
      } else {
        _failedAttempts = 0;
      }
      return isCorrect;
    } catch (e) {
      print('Erro ao verificar pergunta de segurança: $e');
      _failedAttempts++;
      return false;
    }
  }
  int getRemainingAttempts() {
    return (_maxFailedAttempts - _failedAttempts).clamp(0, _maxFailedAttempts);
  }
  void resetFailedAttempts() {
    _failedAttempts = 0;
    print('Contador de tentativas resetado');
  }
  bool isLockedOut() {
    return _failedAttempts >= _maxFailedAttempts;
  }
}
