import 'dart:convert';
import 'package:crypto/crypto.dart';

/// Modelo que representa os dados extraídos de um QR Code Keeply.
///
/// Formato do QR Code gerado pelo site:
/// ```
/// keeply://pair?t=<token>&h=<host>&u=<userId>&ts=<timestamp>&sig=<hmac_signature>
/// ```
///
/// O [sig] é um HMAC-SHA256 calculado sobre "token|host|userId|timestamp"
/// usando a chave secreta compartilhada. Somente QR codes gerados
/// pelo site Keeply legítimo produzem uma assinatura válida.
class PairingPayload {
  final String token;
  final String host;
  final String userId;
  final int timestamp;
  final String signature;

  /// Chave secreta compartilhada entre o site e o mobile.
  /// Em produção, essa chave seria embutida de forma segura (obfuscada).
  static const String _sharedSecret = 'kply_cert_2026_xK9mPqR7vN3wJ5tL8bY1cA4dF6gH0iE2';

  /// Prefixo obrigatório que identifica um QR Code Keeply.
  static const String keeplyPrefix = 'keeply://pair';

  /// Tolerância máxima de tempo (5 minutos) para aceitar um QR Code.
  static const int maxAgeSeconds = 300;

  PairingPayload({
    required this.token,
    required this.host,
    required this.userId,
    required this.timestamp,
    required this.signature,
  });

  /// Tenta parsear uma string de QR Code.
  /// Retorna null se o formato não for reconhecido como Keeply.
  static PairingPayload? tryParse(String rawData) {
    if (!rawData.startsWith(keeplyPrefix)) return null;

    try {
      final uri = Uri.parse(rawData);
      final t = uri.queryParameters['t'] ?? '';
      final h = uri.queryParameters['h'] ?? '';
      final u = uri.queryParameters['u'] ?? '';
      final ts = uri.queryParameters['ts'] ?? '0';
      final sig = uri.queryParameters['sig'] ?? '';

      if (t.isEmpty || h.isEmpty || sig.isEmpty) return null;

      return PairingPayload(
        token: t,
        host: h,
        userId: u,
        timestamp: int.tryParse(ts) ?? 0,
        signature: sig,
      );
    } catch (_) {
      return null;
    }
  }

  /// Valida a assinatura HMAC-SHA256 do payload.
  /// Garante que o QR foi gerado por um site Keeply legítimo.
  bool isSignatureValid() {
    final expectedSig = _computeSignature(token, host, userId, timestamp);
    return signature == expectedSig;
  }

  /// Verifica se o QR Code não expirou (máximo 5 minutos).
  bool isNotExpired() {
    final now = DateTime.now().millisecondsSinceEpoch ~/ 1000;
    return (now - timestamp).abs() <= maxAgeSeconds;
  }

  /// Validação completa: formato + assinatura + expiração.
  ValidationResult validate() {
    if (token.isEmpty || host.isEmpty) {
      return ValidationResult(valid: false, error: 'Dados incompletos no QR Code');
    }
    if (!isNotExpired()) {
      return ValidationResult(valid: false, error: 'QR Code expirado. Gere um novo no site.');
    }
    if (!isSignatureValid()) {
      return ValidationResult(valid: false, error: 'Certificado inválido. QR Code não reconhecido.');
    }
    return ValidationResult(valid: true);
  }

  /// Calcula a assinatura HMAC-SHA256 esperada.
  static String _computeSignature(String token, String host, String userId, int ts) {
    final message = '$token|$host|$userId|$ts';
    final key = utf8.encode(_sharedSecret);
    final bytes = utf8.encode(message);
    final hmacSha256 = Hmac(sha256, key);
    final digest = hmacSha256.convert(bytes);
    return digest.toString();
  }

  /// Gera a URL WebSocket a partir do host do QR.
  String get wsUrl {
    if (host.startsWith('wss://') || host.startsWith('ws://')) return host;
    return 'wss://$host/ws/agent';
  }

  /// Gera a URL HTTP base a partir do host do QR.
  String get httpBaseUrl {
    if (host.startsWith('https://') || host.startsWith('http://')) return host;
    return 'https://$host';
  }
}

/// Resultado da validação de um QR Code.
class ValidationResult {
  final bool valid;
  final String? error;
  ValidationResult({required this.valid, this.error});
}
