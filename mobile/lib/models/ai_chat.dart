class AiChatMessage {
  final String role;
  final String content;
  final String reasoning;

  const AiChatMessage({
    required this.role,
    required this.content,
    this.reasoning = '',
  });

  Map<String, dynamic> toJson() => {'role': role, 'content': content};
}

class AiChatResponse {
  final String answer;
  final String model;
  final String reasoning;

  const AiChatResponse({
    required this.answer,
    required this.model,
    this.reasoning = '',
  });

  factory AiChatResponse.fromJson(Map<String, dynamic> json) {
    return AiChatResponse(
      answer: json['answer'] as String? ?? '',
      model: json['model'] as String? ?? '',
      reasoning: json['reasoning'] as String? ?? '',
    );
  }
}
