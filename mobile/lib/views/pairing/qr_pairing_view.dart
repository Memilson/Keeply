import 'package:flutter/material.dart';
import 'package:mobile_scanner/mobile_scanner.dart';
import 'dart:convert';
import '../../services/secure_storage_service.dart';
import '../../services/api_client_service.dart';
import 'package:uuid/uuid.dart';

/// [QrPairingView] - Tela de pareamento do dispositivo via QR code.
///
/// Responsabilidades:
/// 1. Exibir câmera para ler QR code
/// 2. Decodificar payload do QR code contendo:
///    - Backend URL
///    - Device name
///    - Código de pareamento (para validação no backend)
/// 3. Salvar dados de pareamento em SecureStorageService
/// 4. Gerar Device ID único
/// 5. Registrar dispositivo no backend
/// 6. Redirecionar para autenticação após sucesso
///
/// Formato esperado do QR code (JSON):
/// ```json
/// {
///   "backendUrl": "http://192.168.1.100:8080",
///   "deviceName": "Galaxy S24",
///   "pairingCode": "ABC123XYZ",
///   "version": "1.0"
/// }
/// ```
///
/// Fluxo:
/// ```
/// SplashView detecta: não pareado
///   ↓
/// QrPairingView exibida
///   ↓
/// Usuário aponta câmera para QR code
///   ↓
/// QR decodificado
///   ↓
/// Dados salvos em SecureStorageService
///   ↓
/// Device ID gerado (UUID)
///   ↓
/// Marcado como pareado
///   ↓
/// Redirecionar para autenticação
/// ```
///
/// Segurança:
/// - QR code contém apenas config inicial
/// - URL e device name salvos em armazenamento seguro
/// - Device ID (UUID) único por dispositivo
/// - Conexão com backend deve usar HTTPS em produção
///
/// Uso:
/// ```dart
/// MaterialApp(
///   routes: {
///     '/pairing': (_) => const QrPairingView(),
///   },
/// )
/// ```
class QrPairingView extends StatefulWidget {
  const QrPairingView({super.key});

  @override
  State<QrPairingView> createState() => _QrPairingViewState();
}

/// [_QrPairingViewState] - Estado e lógica da tela.
class _QrPairingViewState extends State<QrPairingView> {
  /// Controller da câmera para scanner do QR code.
  late MobileScannerController _scannerController;

  /// Serviço de armazenamento seguro.
  final SecureStorageService _secureStorage = SecureStorageService();

  /// Cliente de API para registrar dispositivo no backend.
  final ApiClientService _apiClient = ApiClientService();

  /// Flag para indicar se QR foi detectado (evita múltiplas detecções).
  bool _qrDetected = false;

  /// Flag de carregamento durante processamento do QR.
  bool _isProcessing = false;

  /// Mensagem de status exibida ao usuário.
  String _statusMessage = 'Aponte a câmera para o código QR...';

  /// Flag de erro.
  bool _hasError = false;
  String _errorMessage = '';

  @override
  void initState() {
    super.initState();
    _initializeScanner();
  }

  /// Inicializa o scanner de QR code.
  ///
  /// Configura permissões de câmera e setup inicial.
  void _initializeScanner() {
    _scannerController = MobileScannerController(
      detectionSpeed: DetectionSpeed.noDuplicates,
      autoStart: true,
    );
  }

  /// Processa o QR code detectado.
  ///
  /// Parâmetros:
  /// - [barcode]: Código de barras/QR detectado pela câmera
  ///
  /// Fluxo:
  /// 1. Decodifica payload JSON do QR
  /// 2. Valida campos obrigatórios
  /// 3. Salva em SecureStorageService
  /// 4. Gera Device ID
  /// 5. Marca como pareado
  /// 6. Redireciona para autenticação
  Future<void> _processQrCode(BarcodeCapture barcode) async {
    if (_qrDetected || _isProcessing) return;

    try {
      _qrDetected = true;

      setState(() {
        _isProcessing = true;
        _statusMessage = 'Processando código QR...';
      });

      // Parar scanner
      await _scannerController.stop();

      // Extrair valor do código
      final rawValue = barcode.barcodes.isNotEmpty
          ? barcode.barcodes.first.rawValue
          : null;

      if (rawValue == null || rawValue.isEmpty) {
        throw Exception('Código QR inválido (vazio)');
      }

      print('QR detectado: $rawValue');

      // Decodificar JSON
      late Map<String, dynamic> qrData;
      try {
        qrData = jsonDecode(rawValue) as Map<String, dynamic>;
      } catch (e) {
        throw Exception('Formato QR inválido. Esperado JSON: $e');
      }

      // Validar campos obrigatórios
      final backendUrl = qrData['backendUrl'] as String?;
      final deviceName = qrData['deviceName'] as String?;
      final pairingCode = qrData['pairingCode'] as String?;

      if (backendUrl == null || backendUrl.isEmpty) {
        throw Exception('Campo "backendUrl" não encontrado no QR code');
      }
      if (deviceName == null || deviceName.isEmpty) {
        throw Exception('Campo "deviceName" não encontrado no QR code');
      }
      if (pairingCode == null || pairingCode.isEmpty) {
        throw Exception('Campo "pairingCode" não encontrado no QR code');
      }

      setState(() {
        _statusMessage = 'Salvando configuração...';
      });

      // Gerar Device ID único (UUID)
      final deviceId = const Uuid().v4();

      // Salvar backend URL para requisições subsequentes
      await _secureStorage.saveBackendUrl(backendUrl);
      await _secureStorage.saveDeviceId(deviceId);

      setState(() {
        _statusMessage = 'Conectando ao servidor...';
      });

      // Registrar dispositivo no backend
      // Isso retorna JWT token se bem-sucedido
      final jwtToken = await _apiClient.registerDevice(
        deviceId: deviceId,
        deviceName: deviceName,
        pairingCode: pairingCode,
      );

      setState(() {
        _statusMessage = 'Salvando token de autenticação...';
      });

      // Salvar JWT token para autenticação futura
      await _secureStorage.saveToken(jwtToken);

      setState(() {
        _statusMessage = 'Marcando dispositivo como pareado...';
      });

      // Marcar como pareado
      await _secureStorage.setPairingStatus(true);

      print('Dispositivo pareado com sucesso!');
      print('  Backend: $backendUrl');
      print('  Device ID: $deviceId');
      print('  Device Name: $deviceName');
      print('  JWT Token: ${jwtToken.substring(0, 20)}...');

      // Aguardar um pouco para feedback visual
      await Future.delayed(const Duration(seconds: 1));

      if (!mounted) return;

      // Redirecionar para autenticação
      Navigator.of(context).pushReplacementNamed('/splash');
    } catch (e) {
      print('Erro ao processar QR: $e');

      setState(() {
        _isProcessing = false;
        _qrDetected = false;
        _hasError = true;
        _errorMessage = 'Erro: $e';
      });

      // Retomar scanner após erro
      if (mounted) {
        await Future.delayed(const Duration(seconds: 2));
        _scannerController.start();
      }
    }
  }

      // Somente aceita QR Codes com prefixo Keeply
      if (!rawData.startsWith(PairingPayload.keeplyPrefix)) continue;

      // Encontrou um QR Keeply válido — processar
      _processQrCode(rawData);
      break;
    }
  }

  Future<void> _processQrCode(String rawData) async {
    if (_isProcessing) return;
    _isProcessing = true;

    setState(() {
      _state = _PairingState.validating;
      _statusMessage = 'Validando certificado...';
    });

    // Pausa o scanner enquanto processa
    _scannerController?.stop();

    final result = await widget.ws.identityController.processScannedQrCode(
      rawData,
    );

    if (!mounted) return;

    if (result.success) {
      _stopScanner();
      setState(() {
        _state = _PairingState.paired;
        _statusMessage = 'Pareado com sucesso!';
      });

      // Auto-conecta ao WebSocket
      Future.delayed(const Duration(seconds: 2), () {
        if (mounted && result.identity != null) {
          final config = widget.ws.config;
          config.agentId = result.identity!.deviceId;
          if (result.wsUrl != null) config.url = result.wsUrl!;
          widget.ws.connect(config, result.identity!);
        }
      });
    } else {
      setState(() {
        _state = _PairingState.error;
        _statusMessage = result.error ?? 'Erro desconhecido';
      });

      // Volta ao scanner após 3 segundos
      Future.delayed(const Duration(seconds: 3), () {
        if (mounted && _state == _PairingState.error) {
          _isProcessing = false;
          _scannerController?.start();
          setState(() => _state = _PairingState.scanning);
        }
      });
    }
  }

  void _resetScanner() {
    _isProcessing = false;
    _stopScanner();
    setState(() {
      _state = _PairingState.idle;
      _statusMessage = '';
    });
  }

  Future<void> _unpairDevice() async {
    await widget.ws.identityController.unpair();
    widget.ws.goOffline();
    _resetScanner();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: const Color(0xFF0F172A),
      appBar: AppBar(
        title: const Text(
          'Parear Dispositivo',
          style: TextStyle(
            fontWeight: FontWeight.w700,
            fontSize: 18,
            color: Colors.white,
          ),
        ),
        backgroundColor: const Color(0xFF1E293B),
        elevation: 0,
        automaticallyImplyLeading: false,
        actions: [
          if (_state == _PairingState.paired)
            IconButton(
              icon: const Icon(Icons.link_off, color: Color(0xFFEF4444)),
              tooltip: 'Desparear',
              onPressed: _unpairDevice,
            ),
        ],
      ),
      body: _buildBody(),
    );
  }

  Widget _buildBody() {
    switch (_state) {
      case _PairingState.paired:
        return _buildPairedView();
      case _PairingState.scanning:
      case _PairingState.validating:
      case _PairingState.error:
        return _buildScannerView();
  @override
  void dispose() {
    _scannerController.dispose();
    _apiClient.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: _isProcessing
          ? _buildProcessingWidget()
          : Stack(
              children: [
                // Câmera
                MobileScanner(
                  controller: _scannerController,
                  onDetect: _processQrCode,
                  errorBuilder: (context, error, child) {
                    return Center(
                      child: Column(
                        mainAxisAlignment: MainAxisAlignment.center,
                        children: [
                          const Icon(
                            Icons.camera_alt_outlined,
                            size: 64,
                            color: Colors.grey,
                          ),
                          const SizedBox(height: 16),
                          Text(
                            'Erro ao acessar câmera',
                            style: TextStyle(
                              fontSize: 16,
                              color: Colors.grey[400],
                            ),
                          ),
                          const SizedBox(height: 24),
                          ElevatedButton.icon(
                            onPressed: () {
                              setState(() {
                                _scannerController.start();
                              });
                            },
                            icon: const Icon(Icons.refresh),
                            label: const Text('Tentar Novamente'),
                          ),
                        ],
                      ),
                    );
                  },
                ),

                // Overlay com instruções
                Positioned(
                  top: 0,
                  left: 0,
                  right: 0,
                  child: Container(
                    color: Colors.black.withOpacity(0.5),
                    child: SafeArea(
                      child: Padding(
                        padding: const EdgeInsets.all(16),
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            const Text(
                              'Pareamento do Dispositivo',
                              style: TextStyle(
                                fontSize: 20,
                                fontWeight: FontWeight.bold,
                                color: Colors.white,
                              ),
                            ),
                            const SizedBox(height: 8),
                            Text(
                              'Escaneie o código QR de pareamento',
                              style: TextStyle(
                                fontSize: 14,
                                color: Colors.grey[300],
                              ),
                            ),
                          ],
                        ),
                      ),
                    ),
                  ),
                ),

                // Indicador de scan (retângulo destacado)
                Positioned(
                  top: 0,
                  bottom: 0,
                  left: 0,
                  right: 0,
                  child: Align(
                    alignment: Alignment.center,
                    child: Column(
                      mainAxisAlignment: MainAxisAlignment.center,
                      children: [
                        Container(
                          width: 280,
                          height: 280,
                          decoration: BoxDecoration(
                            border: Border.all(
                              color: const Color(0xFF3B82F6),
                              width: 3,
                            ),
                            borderRadius: BorderRadius.circular(12),
                            boxShadow: [
                              BoxShadow(
                                color: const Color(0xFF3B82F6).withOpacity(0.3),
                                blurRadius: 20,
                                spreadRadius: 10,
                              ),
                            ],
                          ),
                        ),
                        const SizedBox(height: 48),
                        Text(
                          'Posicione o QR code dentro do quadro',
                          style: TextStyle(
                            fontSize: 14,
                            color: Colors.white,
                            backgroundColor: Colors.black.withOpacity(0.6),
                          ),
                          textAlign: TextAlign.center,
                        ),
                      ],
                    ),
                  ),
                ),

                // Status message / Erro no topo
                if (_hasError)
                  Positioned(
                    bottom: 32,
                    left: 16,
                    right: 16,
                    child: Container(
                      padding: const EdgeInsets.all(16),
                      decoration: BoxDecoration(
                        color: Colors.red.withOpacity(0.9),
                        borderRadius: BorderRadius.circular(8),
                      ),
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          const Row(
                            children: [
                              Icon(Icons.error_outline, color: Colors.white),
                              SizedBox(width: 8),
                              Text(
                                'Erro de Pareamento',
                                style: TextStyle(
                                  fontSize: 14,
                                  fontWeight: FontWeight.bold,
                                  color: Colors.white,
                                ),
                              ),
                            ],
                          ),
                          const SizedBox(height: 8),
                          Text(
                            _errorMessage,
                            style: const TextStyle(
                              fontSize: 12,
                              color: Colors.white70,
                            ),
                          ),
                          const SizedBox(height: 12),
                          ElevatedButton(
                            onPressed: () {
                              setState(() {
                                _hasError = false;
                                _errorMessage = '';
                              });
                            },
                            style: ElevatedButton.styleFrom(
                              backgroundColor: Colors.white,
                            ),
                            child: const Text(
                              'Tentar Novamente',
                              style: TextStyle(color: Colors.red),
                            ),
                          ),
                        ],
                      ),
                    ),
                  ),
              ],
            ),
    );
  }

  /// Widget exibido enquanto está processando o QR.
  Widget _buildProcessingWidget() {
    return Container(
      decoration: const BoxDecoration(
        gradient: LinearGradient(
          begin: Alignment.topLeft,
          end: Alignment.bottomRight,
          colors: [
            Color(0xFF0F172A),
            Color(0xFF1E293B),
          ],
        ),
      ),
      child: Center(
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            // Loader animado
            const SizedBox(
              width: 60,
              height: 60,
              child: CircularProgressIndicator(
                valueColor: AlwaysStoppedAnimation<Color>(
                  Color(0xFF3B82F6),
                ),
                strokeWidth: 3,
              ),
            ),

            const SizedBox(height: 24),

            // Status text
            Text(
              _statusMessage,
              style: const TextStyle(
                fontSize: 16,
                fontWeight: FontWeight.w500,
                color: Colors.white,
              ),
              textAlign: TextAlign.center,
            ),

            const SizedBox(height: 8),

            Text(
              'Não feche o aplicativo',
              style: TextStyle(
                fontSize: 12,
                color: Colors.grey[400],
              ),
            ),
          ],
        ),
      ),
    );
  }
}

              child: ElevatedButton.icon(
                onPressed: _startScanner,
                icon: const Icon(Icons.qr_code_scanner_rounded, size: 20),
                label: const Text(
                  'Escanear QR Code',
                  style: TextStyle(fontSize: 15, fontWeight: FontWeight.w600),
                ),
                style: ElevatedButton.styleFrom(
                  backgroundColor: const Color(0xFF3B82F6),
                  foregroundColor: Colors.white,
                  padding: const EdgeInsets.symmetric(vertical: 16),
                  shape: RoundedRectangleBorder(
                    borderRadius: BorderRadius.circular(14),
                  ),
                ),
              ),
            ),
            const SizedBox(height: 16),

            // Info sobre pareamento online
            Container(
              width: double.infinity,
              padding: const EdgeInsets.all(16),
              decoration: BoxDecoration(
                color: const Color(0xFF1E293B),
                borderRadius: BorderRadius.circular(16),
                border: Border.all(color: const Color(0xFF334155)),
              ),
              child: Column(
                children: [
                  Row(
                    children: [
                      Container(
                        padding: const EdgeInsets.all(8),
                        decoration: BoxDecoration(
                          color: const Color(
                            0xFF06B6D4,
                          ).withValues(alpha: 0.15),
                          borderRadius: BorderRadius.circular(10),
                        ),
                        child: const Icon(
                          Icons.cloud_queue_rounded,
                          color: Color(0xFF06B6D4),
                          size: 18,
                        ),
                      ),
                      const SizedBox(width: 12),
                      const Expanded(
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Text(
                              'Aguardando pareamento',
                              style: TextStyle(
                                color: Color(0xFF06B6D4),
                                fontWeight: FontWeight.w600,
                                fontSize: 13,
                              ),
                            ),
                            SizedBox(height: 2),
                            Text(
                              'Escaneie o QR Code do site Keeply para conectar o app ao backend.',
                              style: TextStyle(
                                color: Color(0xFF94A3B8),
                                fontSize: 12,
                              ),
                            ),
                          ],
                        ),
                      ),
                    ],
                  ),
                ],
              ),
            ),
            const SizedBox(height: 12),

            // Badge de segurança
            Container(
              padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 10),
              decoration: BoxDecoration(
                color: const Color(0xFF1E293B),
                borderRadius: BorderRadius.circular(12),
                border: Border.all(color: const Color(0xFF334155)),
              ),
              child: const Row(
                mainAxisSize: MainAxisSize.min,
                children: [
                  Icon(Icons.verified_user, color: Color(0xFF22C55E), size: 16),
                  SizedBox(width: 8),
                  Text(
                    'Protegido por certificado HMAC-SHA256',
                    style: TextStyle(color: Color(0xFF94A3B8), fontSize: 11),
                  ),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }

  // ==================== Scanner View ====================

  Widget _buildScannerView() {
    return Column(
      children: [
        // Header com instruções
        Container(
          width: double.infinity,
          padding: const EdgeInsets.all(20),
          decoration: BoxDecoration(
            gradient: LinearGradient(
              colors: [
                const Color(0xFF1E293B),
                const Color(0xFF0F172A).withValues(alpha: 0.0),
              ],
              begin: Alignment.topCenter,
              end: Alignment.bottomCenter,
            ),
          ),
          child: Column(
            children: [
              Icon(
                _state == _PairingState.error
                    ? Icons.warning_amber_rounded
                    : Icons.qr_code_scanner_rounded,
                color: _state == _PairingState.error
                    ? const Color(0xFFEF4444)
                    : const Color(0xFF06B6D4),
                size: 32,
              ),
              const SizedBox(height: 12),
              Text(
                _state == _PairingState.validating
                    ? 'Validando...'
                    : _state == _PairingState.error
                    ? 'QR Code Inválido'
                    : 'Escaneie o QR Code do site Keeply',
                style: const TextStyle(
                  color: Colors.white,
                  fontSize: 16,
                  fontWeight: FontWeight.w600,
                ),
                textAlign: TextAlign.center,
              ),
              const SizedBox(height: 6),
              Text(
                _state == _PairingState.error
                    ? _statusMessage
                    : 'Somente QR Codes gerados pelo site são aceitos',
                style: TextStyle(
                  color: _state == _PairingState.error
                      ? const Color(0xFFEF4444)
                      : const Color(0xFF94A3B8),
                  fontSize: 13,
                ),
                textAlign: TextAlign.center,
              ),
            ],
          ),
        ),

        // Scanner da câmera
        Expanded(
          child: Stack(
            alignment: Alignment.center,
            children: [
              // Câmera
              if (_scannerActive && _scannerController != null)
                ClipRRect(
                  borderRadius: BorderRadius.circular(24),
                  child: Padding(
                    padding: const EdgeInsets.symmetric(horizontal: 24),
                    child: ClipRRect(
                      borderRadius: BorderRadius.circular(24),
                      child: MobileScanner(
                        controller: _scannerController!,
                        onDetect: _onBarcodeDetected,
                      ),
                    ),
                  ),
                ),

              // Overlay com recorte central
              Padding(
                padding: const EdgeInsets.symmetric(horizontal: 24),
                child: ClipRRect(
                  borderRadius: BorderRadius.circular(24),
                  child: CustomPaint(
                    size: Size.infinite,
                    painter: _ScannerOverlayPainter(),
                  ),
                ),
              ),

              // Cantos animados do scanner
              AnimatedBuilder(
                animation: _pulseController,
                builder: (context, child) {
                  final opacity = 0.5 + (_pulseController.value * 0.5);
                  return Opacity(opacity: opacity, child: child);
                },
                child: Container(
                  width: 220,
                  height: 220,
                  decoration: BoxDecoration(
                    borderRadius: BorderRadius.circular(16),
                    border: Border.all(
                      color: _state == _PairingState.validating
                          ? const Color(0xFFF59E0B)
                          : const Color(0xFF06B6D4),
                      width: 2.5,
                    ),
                  ),
                ),
              ),

              // Indicador de processamento
              if (_state == _PairingState.validating)
                Container(
                  width: 220,
                  height: 220,
                  decoration: BoxDecoration(
                    color: Colors.black.withValues(alpha: 0.6),
                    borderRadius: BorderRadius.circular(16),
                  ),
                  child: const Column(
                    mainAxisAlignment: MainAxisAlignment.center,
                    children: [
                      CircularProgressIndicator(
                        color: Color(0xFF06B6D4),
                        strokeWidth: 3,
                      ),
                      SizedBox(height: 16),
                      Text(
                        'Verificando certificado...',
                        style: TextStyle(color: Colors.white, fontSize: 13),
                      ),
                    ],
                  ),
                ),
            ],
          ),
        ),

        // Footer com badge de segurança
        Padding(
          padding: const EdgeInsets.all(24),
          child: Column(
            children: [
              // Badge de segurança
              Container(
                padding: const EdgeInsets.symmetric(
                  horizontal: 16,
                  vertical: 10,
                ),
                decoration: BoxDecoration(
                  color: const Color(0xFF1E293B),
                  borderRadius: BorderRadius.circular(12),
                  border: Border.all(color: const Color(0xFF334155)),
                ),
                child: const Row(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    Icon(
                      Icons.verified_user,
                      color: Color(0xFF22C55E),
                      size: 16,
                    ),
                    SizedBox(width: 8),
                    Text(
                      'Protegido por certificado HMAC-SHA256',
                      style: TextStyle(color: Color(0xFF94A3B8), fontSize: 11),
                    ),
                  ],
                ),
              ),
              const SizedBox(height: 12),

              // Botões de ação
              Row(
                mainAxisAlignment: MainAxisAlignment.center,
                children: [
                  if (_state == _PairingState.error)
                    TextButton.icon(
                      onPressed: () {
                        _isProcessing = false;
                        _scannerController?.start();
                        setState(() => _state = _PairingState.scanning);
                      },
                      icon: const Icon(
                        Icons.refresh,
                        color: Color(0xFF3B82F6),
                        size: 18,
                      ),
                      label: const Text(
                        'Tentar novamente',
                        style: TextStyle(
                          color: Color(0xFF3B82F6),
                          fontSize: 13,
                        ),
                      ),
                    ),
                  TextButton.icon(
                    onPressed: _resetScanner,
                    icon: const Icon(
                      Icons.arrow_back_rounded,
                      color: Color(0xFF64748B),
                      size: 18,
                    ),
                    label: const Text(
                      'Voltar',
                      style: TextStyle(color: Color(0xFF64748B), fontSize: 13),
                    ),
                  ),
                ],
              ),
            ],
          ),
        ),
      ],
    );
  }

  // ==================== Paired View ====================

  Widget _buildPairedView() {
    return Center(
      child: Padding(
        padding: const EdgeInsets.all(32),
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            // Ícone de sucesso animado
            AnimatedBuilder(
              animation: _pulseController,
              builder: (context, child) {
                final scale = 1.0 + (_pulseController.value * 0.06);
                return Transform.scale(scale: scale, child: child);
              },
              child: Container(
                width: 100,
                height: 100,
                decoration: BoxDecoration(
                  shape: BoxShape.circle,
                  gradient: const LinearGradient(
                    colors: [Color(0xFF22C55E), Color(0xFF16A34A)],
                  ),
                  boxShadow: [
                    BoxShadow(
                      color: const Color(0xFF22C55E).withValues(alpha: 0.35),
                      blurRadius: 30,
                      spreadRadius: 4,
                    ),
                  ],
                ),
                child: const Icon(
                  Icons.link_rounded,
                  color: Colors.white,
                  size: 48,
                ),
              ),
            ),
            const SizedBox(height: 32),

            const Text(
              'Dispositivo Pareado',
              style: TextStyle(
                color: Colors.white,
                fontSize: 24,
                fontWeight: FontWeight.w700,
              ),
            ),
            const SizedBox(height: 8),
            const Text(
              'Seu celular está conectado ao site Keeply\ne pronto para sincronizar seus dados.',
              textAlign: TextAlign.center,
              style: TextStyle(color: Color(0xFF94A3B8), fontSize: 14),
            ),
            const SizedBox(height: 32),

            // Info do status
            Container(
              width: double.infinity,
              padding: const EdgeInsets.all(16),
              decoration: BoxDecoration(
                color: const Color(0xFF1E293B),
                borderRadius: BorderRadius.circular(16),
                border: Border.all(color: const Color(0xFF334155)),
              ),
              child: Column(
                children: [
                  _infoRow(
                    Icons.devices,
                    'Status',
                    widget.ws.connected ? 'Online' : 'Reconectando...',
                  ),
                  const SizedBox(height: 10),
                  _infoRow(
                    Icons.shield_outlined,
                    'Segurança',
                    'Certificado HMAC-SHA256',
                  ),
                  const SizedBox(height: 10),
                  _infoRow(Icons.sync, 'Protocolo', 'WebSocket v1'),
                ],
              ),
            ),
            const SizedBox(height: 24),

            // Botão desparear
            OutlinedButton.icon(
              onPressed: _unpairDevice,
              icon: const Icon(
                Icons.link_off,
                color: Color(0xFFEF4444),
                size: 18,
              ),
              label: const Text(
                'Desparear Dispositivo',
                style: TextStyle(color: Color(0xFFEF4444)),
              ),
              style: OutlinedButton.styleFrom(
                padding: const EdgeInsets.symmetric(
                  horizontal: 24,
                  vertical: 12,
                ),
                side: BorderSide(
                  color: const Color(0xFFEF4444).withValues(alpha: 0.4),
                ),
                shape: RoundedRectangleBorder(
                  borderRadius: BorderRadius.circular(12),
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _infoRow(IconData icon, String label, String value) {
    return Row(
      children: [
        Icon(icon, color: const Color(0xFF64748B), size: 16),
        const SizedBox(width: 10),
        SizedBox(
          width: 80,
          child: Text(
            label,
            style: const TextStyle(color: Color(0xFF64748B), fontSize: 12),
          ),
        ),
        Expanded(
          child: Text(
            value,
            style: const TextStyle(color: Color(0xFFE2E8F0), fontSize: 12),
          ),
        ),
      ],
    );
  }
}

// ==================== Estados ====================

enum _PairingState { idle, scanning, validating, paired, error }

// ==================== Overlay do Scanner ====================

class _ScannerOverlayPainter extends CustomPainter {
  @override
  void paint(Canvas canvas, Size size) {
    final paint = Paint()
      ..color = const Color(0xFF0F172A).withValues(alpha: 0.7);

    final scanArea = Rect.fromCenter(
      center: Offset(size.width / 2, size.height / 2),
      width: 220,
      height: 220,
    );

    // Escurece tudo exceto a área de scan
    canvas.drawPath(
      Path.combine(
        PathOperation.difference,
        Path()..addRect(Rect.fromLTWH(0, 0, size.width, size.height)),
        Path()..addRRect(
          RRect.fromRectAndRadius(scanArea, const Radius.circular(16)),
        ),
      ),
      paint,
    );
  }

  @override
  bool shouldRepaint(covariant CustomPainter oldDelegate) => false;
}
