class AiChatMessage {
  final String role;
  final String content;

  const AiChatMessage({required this.role, required this.content});

  Map<String, dynamic> toJson() => {'role': role, 'content': content};
}

class AiChatResponse {
  final String answer;
  final String model;

  const AiChatResponse({required this.answer, required this.model});

  factory AiChatResponse.fromJson(Map<String, dynamic> json) {
    return AiChatResponse(
      answer: json['answer'] as String? ?? '',
      model: json['model'] as String? ?? '',
    );
  }
}
