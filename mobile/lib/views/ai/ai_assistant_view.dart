import 'package:flutter/material.dart';
import '../../models/ai_chat.dart';
import '../../services/api_client_service.dart';

class AiAssistantView extends StatefulWidget {
  const AiAssistantView({super.key});

  @override
  State<AiAssistantView> createState() => _AiAssistantViewState();
}

class _AiAssistantViewState extends State<AiAssistantView> {
  static const Color _azure = Color(0xFF007FFF);
  static const Color _background = Color(0xFF050816);
  static const Color _surface = Color(0xFF111827);
  static const Color _border = Color(0xFF1F2937);
  static const List<String> _suggestions = [
    'Como verifico se meus backups estão saudáveis?',
    'O que fazer quando uma máquina fica offline?',
    'Como funciona a restauração de um snapshot?',
  ];

  final ApiClientService _apiClient = ApiClientService();
  final TextEditingController _messageController = TextEditingController();
  final ScrollController _scrollController = ScrollController();
  final List<AiChatMessage> _messages = [
    const AiChatMessage(
      role: 'assistant',
      content:
          'Sou o Keeply I.A. Posso ajudar com backups, máquinas, snapshots, restauração e diagnóstico.',
    ),
  ];
  bool _isLoading = false;
  String? _errorMessage;
  String? _modelName;

  @override
  void dispose() {
    _messageController.dispose();
    _scrollController.dispose();
    super.dispose();
  }

  List<AiChatMessage> get _history => _messages
      .skip(1)
      .where((message) => message.content.trim().isNotEmpty)
      .toList()
      .reversed
      .take(8)
      .toList()
      .reversed
      .toList();

  Future<void> _sendMessage(String text) async {
    final question = text.trim();
    if (question.isEmpty || _isLoading) return;

    final history = _history;
    setState(() {
      _messages.add(AiChatMessage(role: 'user', content: question));
      _messageController.clear();
      _isLoading = true;
      _errorMessage = null;
    });
    _scrollToBottom();

    try {
      final response = await _apiClient.chatWithAi(
        message: question,
        history: history,
      );
      if (!mounted) return;
      setState(() {
        _messages.add(
          AiChatMessage(
            role: 'assistant',
            content: response.answer,
            reasoning: response.reasoning,
          ),
        );
        _modelName = response.model.isEmpty ? _modelName : response.model;
        _isLoading = false;
      });
      _scrollToBottom();
    } on TokenExpiredException {
      if (!mounted) return;
      Navigator.of(context).pushNamedAndRemoveUntil('/splash', (_) => false);
    } on ApiException catch (e) {
      if (!mounted) return;
      setState(() {
        _errorMessage = e.message;
        _isLoading = false;
      });
    } catch (e) {
      if (!mounted) return;
      setState(() {
        _errorMessage = 'Falha ao consultar o Keeply I.A: $e';
        _isLoading = false;
      });
    }
  }

  void _scrollToBottom() {
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (!_scrollController.hasClients) return;
      _scrollController.animateTo(
        _scrollController.position.maxScrollExtent,
        duration: const Duration(milliseconds: 220),
        curve: Curves.easeOut,
      );
    });
  }

  Widget _buildSuggestion(String text) {
    return OutlinedButton(
      onPressed: _isLoading ? null : () => _sendMessage(text),
      style: OutlinedButton.styleFrom(
        foregroundColor: const Color(0xFFE2E8F0),
        side: BorderSide(color: _azure.withValues(alpha: 0.35)),
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(8)),
        padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 10),
      ),
      child: Text(
        text,
        textAlign: TextAlign.left,
        style: const TextStyle(fontSize: 12, height: 1.25),
      ),
    );
  }

  Widget _buildMessage(AiChatMessage message) {
    final isUser = message.role == 'user';
    final hasReasoning = !isUser && message.reasoning.trim().isNotEmpty;
    return Align(
      alignment: isUser ? Alignment.centerRight : Alignment.centerLeft,
      child: ConstrainedBox(
        constraints: BoxConstraints(
          maxWidth: MediaQuery.sizeOf(context).width * 0.88,
        ),
        child: Container(
          margin: const EdgeInsets.symmetric(vertical: 4),
          padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 10),
          decoration: BoxDecoration(
            color: isUser ? _azure.withValues(alpha: 0.20) : _surface,
            borderRadius: BorderRadius.circular(8),
            border: Border.all(color: isUser ? _azure : _border),
          ),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              if (hasReasoning) ...[
                _buildReasoningPanel(message.reasoning),
                const SizedBox(height: 10),
              ],
              Text(
                message.content,
                style: TextStyle(
                  color: isUser ? Colors.white : const Color(0xFFCBD5E1),
                  fontSize: 14,
                  height: 1.45,
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }

  Widget _buildReasoningPanel(String text) {
    return Theme(
      data: Theme.of(context).copyWith(dividerColor: Colors.transparent),
      child: Container(
        decoration: BoxDecoration(
          color: _background,
          borderRadius: BorderRadius.circular(8),
          border: Border.all(color: _azure.withValues(alpha: 0.35)),
        ),
        child: ExpansionTile(
          dense: true,
          tilePadding: const EdgeInsets.symmetric(horizontal: 10),
          childrenPadding: const EdgeInsets.fromLTRB(10, 0, 10, 10),
          iconColor: _azure,
          collapsedIconColor: _azure,
          title: const Text(
            'Análise',
            style: TextStyle(
              color: Color(0xFFE2E8F0),
              fontSize: 12,
              fontWeight: FontWeight.w700,
            ),
          ),
          children: [
            Align(
              alignment: Alignment.centerLeft,
              child: Text(
                text,
                style: const TextStyle(
                  color: Color(0xFFCBD5E1),
                  fontSize: 12,
                  height: 1.35,
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildComposer() {
    return SafeArea(
      top: false,
      child: Container(
        padding: const EdgeInsets.fromLTRB(16, 12, 16, 14),
        decoration: BoxDecoration(
          color: const Color(0xFF08071A),
          border: Border(
            top: BorderSide(color: _azure.withValues(alpha: 0.18)),
          ),
        ),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            if (_messages.length == 1) ...[
              Wrap(
                spacing: 8,
                runSpacing: 8,
                children: _suggestions.map(_buildSuggestion).toList(),
              ),
              const SizedBox(height: 12),
            ],
            if (_errorMessage != null) ...[
              Container(
                width: double.infinity,
                margin: const EdgeInsets.only(bottom: 10),
                padding: const EdgeInsets.all(10),
                decoration: BoxDecoration(
                  color: const Color(0xFF7F1D1D).withValues(alpha: 0.22),
                  borderRadius: BorderRadius.circular(10),
                  border: Border.all(
                    color: const Color(0xFFEF4444).withValues(alpha: 0.28),
                  ),
                ),
                child: Text(
                  _errorMessage!,
                  style: const TextStyle(
                    color: Color(0xFFFCA5A5),
                    fontSize: 12,
                  ),
                ),
              ),
            ],
            Row(
              children: [
                Expanded(
                  child: TextField(
                    controller: _messageController,
                    enabled: !_isLoading,
                    maxLength: 4000,
                    minLines: 1,
                    maxLines: 4,
                    style: const TextStyle(color: Colors.white),
                    decoration: InputDecoration(
                      counterText: '',
                      hintText: 'Pergunte sobre seus backups...',
                      hintStyle: TextStyle(color: Colors.grey[600]),
                      filled: true,
                      fillColor: const Color(0xFF0D0C22),
                      contentPadding: const EdgeInsets.symmetric(
                        horizontal: 14,
                        vertical: 12,
                      ),
                      border: OutlineInputBorder(
                        borderRadius: BorderRadius.circular(12),
                        borderSide: BorderSide(
                          color: _azure.withValues(alpha: 0.3),
                        ),
                      ),
                      enabledBorder: OutlineInputBorder(
                        borderRadius: BorderRadius.circular(12),
                        borderSide: BorderSide(
                          color: _azure.withValues(alpha: 0.25),
                        ),
                      ),
                      focusedBorder: OutlineInputBorder(
                        borderRadius: BorderRadius.circular(12),
                        borderSide: const BorderSide(color: _azure),
                      ),
                    ),
                    onSubmitted: _sendMessage,
                  ),
                ),
                const SizedBox(width: 10),
                SizedBox(
                  width: 48,
                  height: 48,
                  child: FilledButton(
                    onPressed: _isLoading
                        ? null
                        : () => _sendMessage(_messageController.text),
                    style: FilledButton.styleFrom(
                      backgroundColor: _azure,
                      foregroundColor: Colors.white,
                      disabledBackgroundColor: const Color(
                        0xFF007FFF,
                      ).withValues(alpha: 0.35),
                      padding: EdgeInsets.zero,
                      shape: RoundedRectangleBorder(
                        borderRadius: BorderRadius.circular(12),
                      ),
                    ),
                    child: _isLoading
                        ? const SizedBox(
                            width: 18,
                            height: 18,
                            child: CircularProgressIndicator(
                              strokeWidth: 2,
                              valueColor: AlwaysStoppedAnimation<Color>(
                                Colors.white,
                              ),
                            ),
                          )
                        : const Icon(Icons.send_rounded, size: 20),
                  ),
                ),
              ],
            ),
          ],
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
              'Keeply I.A',
              style: TextStyle(
                fontSize: 24,
                fontWeight: FontWeight.bold,
                color: Colors.white,
              ),
            ),
            const SizedBox(height: 4),
            Text(
              _modelName ?? 'Assistente via API do backend',
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
              style: TextStyle(
                fontSize: 13,
                color: Colors.grey[400],
                fontWeight: FontWeight.normal,
              ),
            ),
          ],
        ),
        actions: [
          IconButton(
            icon: const Icon(Icons.refresh, color: Colors.white70),
            tooltip: 'Limpar conversa',
            onPressed: _isLoading
                ? null
                : () {
                    setState(() {
                      _messages
                        ..clear()
                        ..add(
                          const AiChatMessage(
                            role: 'assistant',
                            content:
                                'Sou o Keeply I.A. Posso ajudar com backups, máquinas, snapshots, restauração e diagnóstico.',
                          ),
                        );
                      _errorMessage = null;
                    });
                  },
          ),
        ],
      ),
      body: Column(
        children: [
          Expanded(
            child: ListView.builder(
              controller: _scrollController,
              padding: const EdgeInsets.fromLTRB(16, 10, 16, 18),
              physics: const BouncingScrollPhysics(),
              itemCount: _messages.length + (_isLoading ? 1 : 0),
              itemBuilder: (context, index) {
                if (index == _messages.length) {
                  return Align(
                    alignment: Alignment.centerLeft,
                    child: Container(
                      margin: const EdgeInsets.symmetric(vertical: 5),
                      padding: const EdgeInsets.symmetric(
                        horizontal: 14,
                        vertical: 11,
                      ),
                      decoration: BoxDecoration(
                        color: const Color(0xFF15142B),
                        borderRadius: BorderRadius.circular(12),
                        border: Border.all(
                          color: Colors.white.withValues(alpha: 0.07),
                        ),
                      ),
                      child: const Text(
                        'Pensando...',
                        style: TextStyle(
                          color: Color(0xFF94A3B8),
                          fontSize: 14,
                        ),
                      ),
                    ),
                  );
                }
                return _buildMessage(_messages[index]);
              },
            ),
          ),
          _buildComposer(),
        ],
      ),
    );
  }
}
