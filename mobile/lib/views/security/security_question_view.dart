import 'package:flutter/material.dart';
import '../../services/biometric_security_service.dart';
class SecurityQuestionView extends StatefulWidget {
  const SecurityQuestionView({super.key});
  @override
  State<SecurityQuestionView> createState() => _SecurityQuestionViewState();
}
class _SecurityQuestionViewState extends State<SecurityQuestionView> {
  final BiometricSecurityService _bioSecurity = BiometricSecurityService();
  late TextEditingController _answerController;
  SecurityQuestion? _currentQuestion;
  bool _isLoading = false;
  bool _showError = false;
  String _errorMessage = '';
  bool _isLockedOut = false;
  @override
  void initState() {
    super.initState();
    _answerController = TextEditingController();
    _loadSecurityQuestion();
  }
  Future<void> _loadSecurityQuestion() async {
    try {
      final question = await _bioSecurity.getSecurityQuestion();
      if (!mounted) return;
      setState(() {
        _currentQuestion = question;
        _answerController.clear();
        _showError = false;
        _errorMessage = '';
      });
    } catch (e) {
      print('Erro ao carregar pergunta de segurança: $e');
      if (mounted) {
        setState(() {
          _errorMessage = 'Erro ao carregar pergunta. Tente novamente.';
          _showError = true;
        });
      }
    }
  }
  Future<void> _submitAnswer() async {
    try {
      if (_currentQuestion == null) {
        setState(() {
          _errorMessage = 'Pergunta não carregada. Tente novamente.';
          _showError = true;
        });
        return;
      }
      if (_answerController.text.isEmpty) {
        setState(() {
          _errorMessage = 'Porfavor, digite sua resposta.';
          _showError = true;
        });
        return;
      }
      setState(() {
        _isLoading = true;
        _showError = false;
      });
      if (_bioSecurity.isLockedOut()) {
        setState(() {
          _isLoading = false;
          _isLockedOut = true;
          _errorMessage =
              'Você foi bloqueado por excesso de tentativas incorretas. Tente novamente mais tarde.';
          _showError = true;
        });
        return;
      }
      final isCorrect = await _bioSecurity.verifySecurityAnswer(
        _currentQuestion!.id,
        _answerController.text,
      );
      if (!mounted) return;
      setState(() {
        _isLoading = false;
      });
      if (isCorrect) {
        print('Resposta correta! Acessando arquivos...');
        if (mounted) {
          Navigator.of(context).pushNamedAndRemoveUntil('/main', (route) => false);
        }
      } else {
        final remaining = _bioSecurity.getRemainingAttempts();
        setState(() {
          _showError = true;
          if (remaining > 0) {
            _errorMessage =
                'Resposta incorreta. $remaining tentativa${remaining > 1 ? 's' : ''} restante${remaining > 1 ? 's' : ''}.';
          } else {
            _isLockedOut = true;
            _errorMessage = 'Você foi bloqueado. Tente novamente mais tarde.';
          }
          _answerController.clear();
        });
      }
    } catch (e) {
      print('Erro ao validar resposta: $e');
      if (mounted) {
        setState(() {
          _isLoading = false;
          _showError = true;
          _errorMessage = 'Erro ao validar resposta: $e';
        });
      }
    }
  }
  void _loadAnotherQuestion() {
    _loadSecurityQuestion();
  }
  @override
  void dispose() {
    _answerController.dispose();
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
            colors: [Color(0xFF0F172A), Color(0xFF1E293B)],
          ),
        ),
        child: SafeArea(
          child: _isLockedOut ? _buildLockedOutWidget() : _buildMainWidget(),
        ),
      ),
    );
  }
  Widget _buildMainWidget() {
    return SingleChildScrollView(
      padding: const EdgeInsets.all(24),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          const Text(
            'Verificação de Segurança',
            style: TextStyle(
              fontSize: 24,
              fontWeight: FontWeight.bold,
              color: Colors.white,
            ),
          ),
          const SizedBox(height: 8),
          Text(
            'Responda a pergunta abaixo para acessar seus arquivos',
            style: TextStyle(fontSize: 14, color: Colors.grey[400]),
          ),
          const SizedBox(height: 32),
          Container(
            padding: const EdgeInsets.all(20),
            decoration: BoxDecoration(
              color: const Color(0xFF1E293B),
              borderRadius: BorderRadius.circular(12),
              border: Border.all(color: const Color(0xFF334155), width: 1),
            ),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  'Pergunta de Segurança',
                  style: TextStyle(
                    fontSize: 12,
                    color: Colors.grey[500],
                    fontWeight: FontWeight.w600,
                  ),
                ),
                const SizedBox(height: 12),
                if (_currentQuestion != null)
                  Text(
                    _currentQuestion!.question,
                    style: const TextStyle(
                      fontSize: 16,
                      color: Colors.white,
                      fontWeight: FontWeight.w500,
                      height: 1.5,
                    ),
                  )
                else
                  const SizedBox(
                    height: 20,
                    child: CircularProgressIndicator(
                      strokeWidth: 2,
                      valueColor: AlwaysStoppedAnimation<Color>(
                        Color(0xFF3B82F6),
                      ),
                    ),
                  ),
              ],
            ),
          ),
          const SizedBox(height: 32),
          Text(
            'Sua Resposta',
            style: TextStyle(
              fontSize: 14,
              color: Colors.grey[400],
              fontWeight: FontWeight.w600,
            ),
          ),
          const SizedBox(height: 8),
          TextField(
            controller: _answerController,
            enabled: !_isLoading,
            obscureText: false,
            decoration: InputDecoration(
              hintText: 'Digite sua resposta aqui...',
              hintStyle: TextStyle(color: Colors.grey[600]),
              prefixIcon: const Icon(
                Icons.lock_outline,
                color: Color(0xFF3B82F6),
              ),
              suffixIcon: _answerController.text.isNotEmpty
                  ? GestureDetector(
                      onTap: _answerController.clear,
                      child: const Icon(Icons.close, color: Colors.grey),
                    )
                  : null,
              filled: true,
              fillColor: const Color(0xFF1E293B),
              border: OutlineInputBorder(
                borderRadius: BorderRadius.circular(8),
                borderSide: const BorderSide(
                  color: Color(0xFF334155),
                  width: 1,
                ),
              ),
              enabledBorder: OutlineInputBorder(
                borderRadius: BorderRadius.circular(8),
                borderSide: const BorderSide(
                  color: Color(0xFF334155),
                  width: 1,
                ),
              ),
              focusedBorder: OutlineInputBorder(
                borderRadius: BorderRadius.circular(8),
                borderSide: const BorderSide(
                  color: Color(0xFF3B82F6),
                  width: 2,
                ),
              ),
            ),
            style: const TextStyle(color: Colors.white),
            onChanged: (_) => setState(() {}),
            onSubmitted: (_) => _submitAnswer(),
          ),
          if (_showError)
            Padding(
              padding: const EdgeInsets.only(top: 12),
              child: Row(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  const Icon(Icons.error_outline, color: Colors.red, size: 20),
                  const SizedBox(width: 8),
                  Expanded(
                    child: Text(
                      _errorMessage,
                      style: const TextStyle(color: Colors.red, fontSize: 13),
                    ),
                  ),
                ],
              ),
            ),
          const SizedBox(height: 32),
          SizedBox(
            width: double.infinity,
            height: 48,
            child: ElevatedButton.icon(
              onPressed: _isLoading ? null : _submitAnswer,
              style: ElevatedButton.styleFrom(
                backgroundColor: const Color(0xFF3B82F6),
                disabledBackgroundColor: Colors.grey[700],
                shape: RoundedRectangleBorder(
                  borderRadius: BorderRadius.circular(8),
                ),
              ),
              icon: _isLoading
                  ? const SizedBox(
                      width: 20,
                      height: 20,
                      child: CircularProgressIndicator(
                        strokeWidth: 2,
                        valueColor: AlwaysStoppedAnimation<Color>(Colors.white),
                      ),
                    )
                  : const Icon(Icons.check),
              label: Text(
                _isLoading ? 'Verificando...' : 'Enviar Resposta',
                style: const TextStyle(
                  fontSize: 16,
                  fontWeight: FontWeight.w600,
                ),
              ),
            ),
          ),
          const SizedBox(height: 16),
          SizedBox(
            width: double.infinity,
            height: 48,
            child: OutlinedButton.icon(
              onPressed: _isLoading ? null : _loadAnotherQuestion,
              style: OutlinedButton.styleFrom(
                side: const BorderSide(color: Color(0xFF334155), width: 1),
                shape: RoundedRectangleBorder(
                  borderRadius: BorderRadius.circular(8),
                ),
              ),
              icon: const Icon(Icons.refresh),
              label: const Text(
                'Outra Pergunta',
                style: TextStyle(fontSize: 16),
              ),
            ),
          ),
          const SizedBox(height: 32),
          Container(
            padding: const EdgeInsets.all(12),
            decoration: BoxDecoration(
              color: const Color(0xFF1E293B).withValues(alpha: 0.5),
              borderRadius: BorderRadius.circular(8),
              border: Border.all(color: Colors.blue.withValues(alpha: 0.3), width: 1),
            ),
            child: Row(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                const Icon(
                  Icons.info_outline,
                  color: Color(0xFF3B82F6),
                  size: 18,
                ),
                const SizedBox(width: 8),
                Expanded(
                  child: Text(
                    'Digite a resposta em minúsculas, sem caracteres especiais.',
                    style: TextStyle(
                      fontSize: 12,
                      color: Colors.grey[400],
                      height: 1.4,
                    ),
                  ),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }
  Widget _buildLockedOutWidget() {
    return Center(
      child: Column(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          Container(
            width: 100,
            height: 100,
            decoration: BoxDecoration(
              shape: BoxShape.circle,
              border: Border.all(color: Colors.red, width: 2),
            ),
            child: const Center(
              child: Icon(Icons.lock, color: Colors.red, size: 56),
            ),
          ),
          const SizedBox(height: 24),
          const Text(
            'Acesso Bloqueado',
            style: TextStyle(
              fontSize: 20,
              fontWeight: FontWeight.bold,
              color: Colors.white,
            ),
          ),
          const SizedBox(height: 12),
          Padding(
            padding: const EdgeInsets.symmetric(horizontal: 32),
            child: Text(
              'Você excedeu o limite de tentativas. Tente novamente mais tarde.',
              style: TextStyle(fontSize: 14, color: Colors.grey[400]),
              textAlign: TextAlign.center,
            ),
          ),
          const SizedBox(height: 32),
          ElevatedButton.icon(
            onPressed: () {
              _bioSecurity.resetFailedAttempts();
              setState(() {
                _isLockedOut = false;
                _showError = false;
              });
              _loadSecurityQuestion();
            },
            style: ElevatedButton.styleFrom(
              backgroundColor: const Color(0xFF3B82F6),
            ),
            icon: const Icon(Icons.refresh),
            label: const Text('Tentar Novamente'),
          ),
        ],
      ),
    );
  }
}
