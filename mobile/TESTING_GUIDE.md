# 🧪 Guia de Teste Local - Keeply Mobile MVP

## 🚀 Como Testar Sem Backend

Como o backend pode não estar disponível durante desenvolvimento, fornecemos este guia para testar o MVP localmente.

---

## Opção 1: Mock Backend Simples (Sem HTTP Real)

### Modificação Temporária em `ApiClientService`

Para testes, você pode mockar as respostas HTTP sem precisar de servidor real:

```dart
// lib/services/api_client_service.dart - Adicionar este método após _handleError()

/// MÉTODO MOCK - Descomente apenas para testes locais
/// Remove comentário em setMockMode() também se precisar usar este método
Future<String> _mockRegisterDevice({
  required String deviceId,
  required String deviceName,
  required String pairingCode,
}) async {
  // Simular delay de rede
  await Future.delayed(const Duration(seconds: 2));
  
  // Simular JWT (token fake, válido para testes)
  const mockToken = 'eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9.'
      'eyJpc3MiOiJrZWVwbHktbW9jay1iYWNrZW5kIiwiaWF0IjoxNzE3Nzc2MzAwLCJleHAiOjE3Mjc3NzYzMDAsImRldmljZUlkIjoiJHdw'
      'ZGV2aWNlSWQifQ.'
      '_mockSignature123456789';
  
  return mockToken;
}

/// MÉTODO MOCK - Descomente apenas para testes locais
Future<List<RemoteFile>> _mockListFiles({
  String? query,
  int page = 1,
  int pageSize = 50,
}) async {
  // Simular delay de rede
  await Future.delayed(const Duration(milliseconds: 800));
  
  // Simular 100 arquivos totais
  final mockFiles = <RemoteFile>[];
  
  for (int i = (page - 1) * pageSize; i < page * pageSize && i < 100; i++) {
    final fileType = ['pdf', 'jpg', 'doc', 'xls', 'zip'][i % 5];
    final fileName = '${'Arquivo'}${i + 1}.$fileType';
    
    mockFiles.add(RemoteFile(
      id: 'file-mock-$i',
      name: fileName,
      sizeBytes: 1000000 + (i * 500000), // Tamanho incremental
      uploadedAt: DateTime.now().subtract(Duration(days: i)),
    ));
  }
  
  return mockFiles;
}
```

### Usar Mock nos Testes

Edite os métodos públicos para usar mock:

```dart
// No método registerDevice(), comente o código real:

/*
Future<String> registerDevice({...}) async {
  // ... código original ...
}
*/

// E descomente a versão mock:
Future<String> registerDevice({
  required String deviceId,
  required String deviceName,
  required String pairingCode,
}) async {
  return _mockRegisterDevice(
    deviceId: deviceId,
    deviceName: deviceName,
    pairingCode: pairingCode,
  );
}
```

---

## Opção 2: Teste Completo de Fluxo (Sem Modificar Código)

Use este fluxo de teste manual:

### Step 1: Pareamento Manual

1. **Abrir app** → Vai para `SplashView` (2s splash)
2. **Vai para `QrPairingView`** (porque não está pareado)
3. **Simular QR Scanning** → Use este JSON (copie e cole no terminal):

```json
{
  "backendUrl": "http://localhost:8080",
  "deviceName": "Test Device",
  "pairingCode": "TEST-PAIR-CODE",
  "version": "1"
}
```

**Para simular o scan:**
- Criar um arquivo `/tmp/qr_test.json` com o JSON acima
- No código, modificar `_processQrCode` temporariamente para ler de arquivo

OU

4. **Usar físico/emulador com QR real** se conseguir gerar

### Step 2: Autenticação Biométrica

1. **App volta para `SplashView`** (pareado agora)
2. **Tenta biometria** → Emulador Android/iOS mostra prompt
3. **Confirmar biometria** (no emulador, usar `adb shell am` ou Xcode)

```bash
# Android (emulador)
adb shell cmd fingerprint simulate 1

# iOS (Xcode simulator)
# Menu: Simulator > Biometric Enrollment
# Depois: Simulator > Biometric > Matching/Non-Matching Face/Fingerprint
```

### Step 3: Visualizar Arquivos

1. **Sucesso na biometria** → Vai para `FilesListView`
2. **Arquivos carregam** (ou erro se backend não disponível)
3. **Buscar** → Digite algo na SearchBar
4. **Filtrar** → Clique nos chips (Data, Imagens, etc)
5. **Tap em arquivo** → Abre `FilePreviewModal`
6. **Tap "Baixar"** → Tenta fazer download (vai falhar sem backend)

---

## 🔍 Debug: Verificar Estados

### Check 1: Secure Storage

```dart
// Execute no Flutter console:
import 'package:keeply_mobile/services/secure_storage_service.dart';

final storage = SecureStorageService();
print('Token: ${await storage.getToken()}');
print('Backend: ${await storage.getBackendUrl()}');
print('Device ID: ${await storage.getDeviceId()}');
print('Paired: ${await storage.isPaired()}');
print('Configured: ${await storage.isFullyConfigured()}');
```

### Check 2: Biometric Available

```dart
import 'package:keeply_mobile/services/biometric_security_service.dart';

final bio = BiometricSecurityService();
print('Can authenticate: ${await bio.canAuthenticateWithBiometrics()}');
```

### Check 3: API Connection

```dart
import 'package:keeply_mobile/services/api_client_service.dart';

final api = ApiClientService();
try {
  final files = await api.listFiles();
  print('Files loaded: ${files.length}');
} on ApiException catch (e) {
  print('API Error: ${e.message}');
}
```

---

## 🧪 Casos de Teste Manuais

### Teste 1: Primeiro Pareamento
```
✅ App abre
✅ SplashView exibe logo com fade (2s)
✅ Detecta não pareado → QrPairingView
✅ Camera abre (pedir permissão)
✅ Simula QR scan (copiar JSON do backend)
✅ Status: "Processando QR..." → "Salvando..." → "Pareado!"
✅ Volta para SplashView
✅ Biometria prompt aparece
```

### Teste 2: Biometria Sucesso
```
✅ SplashView exibe 2s
✅ Biometria modal aparece
✅ Confirmar biometria no emulador
✅ Sucesso → FilesListView carrega
```

### Teste 3: Biometria Falha → Fallback
```
✅ Biometria prompt aparece
✅ Cancelar/Rejeitar no emulador (2-3x)
✅ Vai para SecurityQuestionView
✅ Pergunta aleatória exibida
✅ Responder errado 3x
✅ "Lockout" message aparece
✅ Forçar app restart
```

### Teste 4: Listar Arquivos
```
✅ Em FilesListView
✅ GET /api/files carregado
✅ Lista de 50 arquivos exibida
✅ Scroll para baixo
✅ Carrega próxima página (50-100)
✅ Scroll infinito funciona
```

### Teste 5: Buscar Arquivos
```
✅ Digite "documento" na SearchBar
✅ Aguarda 500ms debounce
✅ GET /api/files?q=documento
✅ Lista filtra para "documento"
✅ Limpar search (X button)
✅ Volta para lista completa
```

### Teste 6: Filtrar por Tipo
```
✅ Em FilesListView
✅ Clique chip "Imagens"
✅ GET /api/files?filter=images
✅ Mostra só imagens
✅ Clique "PDF"
✅ Mostra só PDFs
```

### Teste 7: Preview e Download
```
✅ Em FilesListView
✅ Tap em arquivo
✅ FilePreviewModal abre (bottom sheet)
✅ Arrasta para cima (drag handle)
✅ Exibe: icon + nome + tamanho + data + info
✅ Tap "Baixar"
✅ Progress bar avança
✅ Se sucesso: "Abrir" button aparece
✅ Arquivo em: /Documents/Keeply/Downloads/
```

---

## 📝 Logs Importantes

### Para Ativar Debug Completo

```dart
// lib/main.dart - Antes do runApp()
// Forçar logs detalhados:

void main() {
  WidgetsFlutterBinding.ensureInitialized();
  
  // Forçar verbosidade
  debugPrintBeginFrameBanner = true;
  debugPrintEndFrameBanner = true;
  
  runApp(const KeeplyApp());
}
```

### Ver Logs no Terminal

```bash
# Flutter logs em tempo real
flutter logs

# Filtrar apenas app logs
flutter logs | grep "flutter"

# Salvar logs em arquivo
flutter logs > app_logs.txt 2>&1
```

### Logs Importantes para Procurar

```
QR detectado: ...
Registrando dispositivo no backend: ...
Dispositivo pareado com sucesso!
  Backend: ...
  Device ID: ...
Iniciando download para: ...
Erro ao processar QR: ...
TokenExpiredException: ...
```

---

## 🔧 Troubleshooting

### Problema: "Camera permission denied"
**Solução**: 
- Android: Ir em Settings > Keeply > Permissions > Camera > Allow
- iOS: Xcode > Simulator > App > Permissions

### Problema: "Biometric not available"
**Solução**:
- Emulador Android: Deve estar em nível 28+, usar Google Play image
- iOS: Xcode > Simulator > Device > Biometric Enrollment > Matching

### Problema: "Backend connection refused"
**Solução**: 
- Deixar Mock Mode ativado temporariamente
- OU iniciar backend: `./gradlew bootRun` no `backend/`

### Problema: "Token expired after restart"
**Solução**: Normal - fazer login novamente
- Isso testa o fluxo de 401 → logout

### Problema: "Files list is empty"
**Solução**:
- Verificar backend: `curl http://localhost:8080/api/files`
- Verificar JWT: `flutter logs | grep "Authorization"`

---

## ✅ Teste Final Completo

Execute este fluxo end-to-end:

```
1. flutter clean
2. flutter pub get
3. flutter run -d <emulator>
4. Esperar SplashView (2s)
5. Escanear QR (ou usar JSON mock)
6. Confirmar biometria
7. Buscar em FilesListView
8. Clicar em arquivo
9. Fazer download
10. Verificar arquivo em /Documents/Keeply/Downloads/
```

Se tudo passar ✅, o MVP está pronto para testes com backend real!

---

## 📊 Checklist de Testes

- [ ] Pareamento QR funciona
- [ ] JWT salvo em SecureStorageService
- [ ] Biometria prompt aparece
- [ ] Fallback perguntas aparece
- [ ] FilesListView carrega
- [ ] Busca filtra arquivos
- [ ] Filtros funcionam
- [ ] Infinite scroll carrega mais
- [ ] Preview modal abre
- [ ] Download inicia
- [ ] Erro é tratado com retry
- [ ] Logout funciona (token expirado)

---

**Nota**: Documentação adicional em `mobile/MVP_DOCUMENTATION.md`
