import 'dart:convert';
import 'dart:math';
import 'package:http/http.dart' as http;
import 'package:shared_preferences/shared_preferences.dart';
import '../models/agent_identity.dart';
import '../models/pairing_payload.dart';
import 'ws_controller.dart';

/// Controller de identidade do agente mobile.
///
/// Novo fluxo de pareamento:
/// 1. O SITE gera um QR Code com token + assinatura HMAC-SHA256
/// 2. O MOBILE escaneia o QR Code com a câmera
/// 3. O mobile valida o certificado (HMAC) para garantir origem legítima
/// 4. O mobile envia confirmação ao backend com seus dados de dispositivo
/// 5. Backend registra o pareamento e retorna userId
/// 6. Mobile conecta via WebSocket
class IdentityController {
  final WsController _ws;

  IdentityController(this._ws);

  /// Carrega identidade já salva. Se já está pareado, retorna direto.
  Future<AgentIdentity> loadIdentity() async {
    AgentIdentity identity = await _loadPersistedIdentity();
    if (identity.deviceId.isEmpty) {
      identity.deviceId = _generateDeviceId();
      identity.fingerprintSha256 = _generateFingerprint();
      await _saveIdentity(identity);
    }
    return identity;
  }

  /// Verifica se o dispositivo já está pareado.
  Future<bool> isPaired() async {
    final identity = await _loadPersistedIdentity();
    return identity.isPaired;
  }

  /// Processa um QR Code escaneado pelo mobile.
  ///
  /// Retorna o resultado do pareamento:
  /// - success: true se pareou com sucesso
  /// - error: mensagem de erro se falhou
  Future<PairingResult> processScannedQrCode(String rawQrData) async {
    // 1. Parsear o QR Code
    final payload = PairingPayload.tryParse(rawQrData);
    if (payload == null) {
      return PairingResult(
        success: false,
        error: 'QR Code não reconhecido. Use apenas QR Codes gerados pelo site Keeply.',
      );
    }

    // 2. Validar certificado (HMAC-SHA256) + expiração
    final validation = payload.validate();
    if (!validation.valid) {
      return PairingResult(success: false, error: validation.error!);
    }

    _ws.addLog('[pairing] QR validado | host=${payload.host}');

    // 3. Carregar/gerar identidade do dispositivo
    AgentIdentity identity = await _loadPersistedIdentity();
    if (identity.deviceId.isEmpty) {
      identity.deviceId = _generateDeviceId();
      identity.fingerprintSha256 = _generateFingerprint();
    }

    // 4. Confirmar pareamento no backend
    try {
      final result = await _confirmPairing(payload, identity);
      
      if (result['status'] == 'active' || result['status'] == 'paired') {
        identity.userId = result['userId'] as String? ?? payload.userId;
        identity.deviceId = result['deviceId'] as String? ?? identity.deviceId;
        identity.pairingCode = '';
        await _saveIdentity(identity);

        _ws.addLog('[pairing] Pareado com sucesso | userId=${identity.userId}');

        return PairingResult(
          success: true,
          identity: identity,
          wsUrl: payload.wsUrl,
          host: payload.host,
        );
      }

      return PairingResult(
        success: false,
        error: 'O servidor recusou o pareamento: ${result['message'] ?? result['status']}',
      );
    } catch (e) {
      _ws.addLog('[pairing] Erro ao confirmar: $e');
      return PairingResult(
        success: false,
        error: 'Falha ao conectar ao servidor: $e',
      );
    }
  }

  /// Confirma o pareamento no backend enviando os dados do dispositivo.
  Future<Map<String, dynamic>> _confirmPairing(
      PairingPayload payload, AgentIdentity identity) async {
    final url = '${payload.httpBaseUrl}/api/devices/pairing/confirm';
    final body = jsonEncode({
      'token': payload.token,
      'deviceId': identity.deviceId,
      'deviceName': _ws.config.deviceName,
      'hostName': _ws.config.hostName,
      'os': _ws.config.osName,
      'certFingerprintSha256': identity.fingerprintSha256,
    });

    final resp = await http.post(
      Uri.parse(url),
      headers: {'Content-Type': 'application/json'},
      body: body,
    ).timeout(const Duration(seconds: 15));

    if (resp.statusCode < 200 || resp.statusCode >= 300) {
      throw Exception('HTTP ${resp.statusCode}: ${resp.body}');
    }
    return jsonDecode(resp.body) as Map<String, dynamic>;
  }

  /// Despareia o dispositivo (remove identidade local).
  Future<void> unpair() async {
    final prefs = await SharedPreferences.getInstance();
    final keys = ['device_id', 'user_id', 'fingerprint_sha256', 'pairing_code', 'cert_pem', 'key_pem'];
    for (final key in keys) {
      await prefs.remove('keeply_$key');
    }
    _ws.addLog('[identity] Dispositivo despareado');
  }

  // ==================== Métodos Internos ====================

  String _generateDeviceId() {
    final rng = Random.secure();
    final bytes = List.generate(16, (_) => rng.nextInt(256));
    return 'dev_${bytes.map((b) => b.toRadixString(16).padLeft(2, '0')).join()}';
  }

  String _generateFingerprint() {
    final rng = Random.secure();
    final bytes = List.generate(32, (_) => rng.nextInt(256));
    return bytes.map((b) => b.toRadixString(16).padLeft(2, '0')).join();
  }

  Future<void> _saveIdentity(AgentIdentity identity) async {
    final prefs = await SharedPreferences.getInstance();
    final meta = identity.toMeta();
    for (final entry in meta.entries) {
      await prefs.setString('keeply_${entry.key}', entry.value);
    }
  }

  Future<AgentIdentity> _loadPersistedIdentity() async {
    final prefs = await SharedPreferences.getInstance();
    final keys = ['device_id', 'user_id', 'fingerprint_sha256', 'pairing_code', 'cert_pem', 'key_pem'];
    final meta = <String, String>{};
    for (final key in keys) {
      meta[key] = prefs.getString('keeply_$key') ?? '';
    }
    return AgentIdentity.fromMeta(meta);
  }
}

/// Resultado do processo de pareamento via QR Code.
class PairingResult {
  final bool success;
  final String? error;
  final AgentIdentity? identity;
  final String? wsUrl;
  final String? host;

  PairingResult({
    required this.success,
    this.error,
    this.identity,
    this.wsUrl,
    this.host,
  });
}
