# 📱 Keeply Mobile - Novo MVP (Refatoring Completo)

## 🎯 Visão Geral

Refatoração completa da interface mobile do Keeply com foco em:
- ✅ **Segurança em Primeiro Lugar**: Biometria + fallback de perguntas de segurança
- ✅ **Pareamento QR**: Simples e seguro via código QR
- ✅ **Interface OneDrive**: Visualização de arquivos em nuvem com busca e filtros
- ✅ **Arquitetura Camadista**: Services (segurança/API) + Views (UI) + Models (dados)

---

## 🏗️ Arquitetura

### Camadas

```
Presentation Layer (Views)
├── SplashViewNew         → Orquestrador de autenticação
├── QrPairingView         → Pareamento via QR
├── SecurityQuestionView  → Fallback biométrico
└── FilesListView         → Tela principal (arquivos)

Business Logic Layer (Services)
├── SecureStorageService  → Keychain/EncryptedSharedPreferences
├── BiometricSecurityService  → Fingerprint + Questions + Lockout
└── ApiClientService      → HTTP com JWT + Retry + Errors

Data Layer (Models)
├── RemoteFile            → Metadados do arquivo
├── AppState              → Estado da aplicação
└── SecurityQuestion      → Pergunta de segurança

```

### Padrões

- **Singleton**: Services (SecureStorageService, BiometricSecurityService, ApiClientService)
- **State Management**: StatefulWidget com setState (MVP simples, sem GetX/Riverpod)
- **Error Handling**: Custom exceptions com try/catch e feedback visual

---

## 🔐 Fluxo de Segurança

### 1️⃣ Pareamento (Primeira Vez)

```
QrPairingView
├─ Escanear QR
├─ Parse JSON { backendUrl, deviceName, pairingCode }
├─ Gerar UUID deviceId
├─ POST /api/devices/register → receber JWT
├─ Salvar em SecureStorageService
│  ├─ Backend URL
│  ├─ Device ID
│  ├─ JWT Token
│  └─ Flag "paired"
└─ Redirecionar para /splash
```

**QR Payload (JSON esperado):**
```json
{
  "backendUrl": "http://localhost:8080",
  "deviceName": "iPhone 14",
  "pairingCode": "PAIR-ABC123-XYZ789",
  "version": "1"
}
```

### 2️⃣ Autenticação (Toda Abertura)

```
SplashViewNew (2 segundos)
├─ Verificar se pareado (SecureStorageService.isFullyConfigured())
│
├─ SE NÃO PAREADO:
│  └─ Redirecionar para /pairing
│
├─ SE PAREADO:
│  ├─ Tentar Biometria (BiometricSecurityService.authenticateWithBiometrics())
│  │
│  ├─ SE BIO OK:
│  │  └─ Redirecionar para /files
│  │
│  └─ SE BIO FALHA:
│     ├─ Apresentar pergunta de segurança
│     ├─ Max 3 tentativas antes de lockout
│     └─ Se resposta correta → /files
│
└─ SE ERRO: Exibir erro e botão retry
```

### 3️⃣ Armazenamento Seguro

**SecureStorageService - Singleton**

| Chave | Valor | Armazenamento |
|-------|-------|---------------|
| `jwt_token` | Token JWT | Keychain (iOS) / EncryptedSharedPreferences (Android) |
| `backend_url` | URL do backend | Keychain / EncryptedSharedPreferences |
| `device_id` | UUID único | Keychain / EncryptedSharedPreferences |
| `pairing_status` | true/false | Keychain / EncryptedSharedPreferences |

**Métodos Públicos:**
- `saveToken(token)` / `getToken()` - JWT
- `saveBackendUrl(url)` / `getBackendUrl()` - Backend
- `saveDeviceId(id)` / `getDeviceId()` - Device ID
- `setPairingStatus(status)` / `isPaired()` - Flag pareado
- `isFullyConfigured()` - Verificação completa
- `clearAll()` - Logout (limpar tudo)

---

## 📡 Integração com Backend

### Endpoints Utilizados

#### 1. Registrar Dispositivo (Pareamento)
```
POST /api/devices/register
Headers:
  Content-Type: application/json
Body:
{
  "deviceId": "550e8400-e29b-41d4-a716-446655440000",
  "deviceName": "iPhone 14",
  "pairingCode": "PAIR-ABC123-XYZ789"
}
Response:
{
  "jwtToken": "eyJ0eXAiOiJKV1QiLC..."
}
```

#### 2. Listar Arquivos (Principal)
```
GET /api/files?page=1&pageSize=50&q=search
Headers:
  Authorization: Bearer <jwtToken>
Response:
{
  "items": [
    {
      "id": "file-id-123",
      "name": "documento.pdf",
      "sizeBytes": 46137344,
      "uploadedAt": "2026-06-08T15:30:00Z"
    }
  ],
  "total": 100
}
```

#### 3. Fazer Download de Arquivo
```
GET /api/files/{id}/download
Headers:
  Authorization: Bearer <jwtToken>
Response:
  <arquivo binário>
```

### Tratamento de Erros

| Status | Exceção | Ação |
|--------|---------|------|
| 401 | `TokenExpiredException` | Logout + redirecionar para /splash |
| 404 | `ResourceNotFoundException` | Exibir "arquivo não encontrado" |
| 429 | `RateLimitException` | Retry com backoff exponencial |
| 5xx | `ServerException` | Retry 3x com backoff |
| Timeout | `NetworkException` | Retry 3x com backoff |

**ApiClientService - Retry Logic:**
- 3 tentativas automáticas
- Backoff exponencial: 1s → 2s → 4s
- Timeout padrão: 15s (download: 2min)

---

## 🎨 Interface Principal (FilesListView)

### Layout

```
┌─────────────────────────────────────┐
│ Keeply                         [☰]  │  ← AppBar
├─────────────────────────────────────┤
│ 🔍 Buscar arquivos...              │  ← SearchBar (live search)
├─────────────────────────────────────┤
│ Recentes  |  Imagens  |  Docs  |... │  ← FilterChips
├─────────────────────────────────────┤
│ 📄 documento.pdf                    │
│    08/06/2026 • 44 MB               │  ← FileItem (tap para preview)
├─────────────────────────────────────┤
│ 🖼️  foto.jpg                         │
│    06/06/2026 • 2.3 MB              │
├─────────────────────────────────────┤
│ ... (scroll infinito)               │
└─────────────────────────────────────┘
```

### Funcionalidades

- **Busca em Tempo Real**: Debounce 500ms → novo GET /api/files?q=busca
- **Filtros**: Data, Imagens, Documentos, PDF (processados no backend)
- **Infinite Scroll**: Carrega próx. página quando scroll atinge 500px do fim
- **Ícones por Tipo**: PDF (vermelho), Imagens (cyan), Docs (azul), etc.
- **Metadados**: Ícone + Nome + Data + Tamanho legível
- **Tap para Preview**: Modal bottom sheet com info + botão download

### Cores (Material 3 Dark)

- Fundo: `#0F172A`
- Surface: `#1E293B`
- Primário: `#3B82F6` (azul)
- Secundário: `#06B6D4` (cyan)
- Erro: `#EF4444` (vermelho)

---

## 📥 Preview e Download (FilePreviewModal)

### Layout Modal

```
┌─────────────────────────────┐
│   ↓ (drag down to close)    │
├─────────────────────────────┤
│ 📄 documento.pdf            │
│ 44 MB • Upd. 08/06/2026     │
├─────────────────────────────┤
│ 📍 Localização: /Backups/   │
│ 🔒 Criptografado (AES-256)  │
│ ✓ Verificado                │
├─────────────────────────────┤
│ [  Baixar Arquivo (44 MB) ] │ ← POST download
│ [  Cancelar               ] │
└─────────────────────────────┘

(Durante download)
────────────────────── 42%
```

### Download Flow

1. Usuário tapa "Baixar Arquivo"
2. GET `/api/files/{id}/download` com JWT
3. Arquivo salvo em `getApplicationDocumentsDirectory()/Keeply/Downloads/`
4. Barra de progresso atualiza
5. Sucesso: botão "Abrir" aparece
6. Erro: mensagem + retry

---

## 🔑 Camadas de Segurança

### 1. Armazenamento de Credenciais
- ✅ JWT token nunca em SharedPreferences plano
- ✅ Keychain (iOS) + EncryptedSharedPreferences (Android)
- ✅ Device ID único por dispositivo

### 2. Autenticação Biométrica
- ✅ Fingerprint / Face ID nativa
- ✅ Fallback: 3 perguntas de segurança com hash SHA-256
- ✅ Lockout automático após 3 falhas

### 3. Comunicação HTTP
- ✅ JWT injetado automaticamente em todo GET/POST
- ✅ Token expirado → redirect para /splash → novo login
- ✅ HTTPS obrigatório em produção

### 4. Injeção de JWT
```dart
// ApiClientService._getHeaders()
Future<Map<String, String>> _getHeaders() async {
  final token = await _secureStorage.getToken();
  return {
    'Authorization': 'Bearer $token',
    'Content-Type': 'application/json',
  };
}
```

---

## 📁 Estrutura de Arquivos

```
mobile/
├── lib/
│   ├── main.dart                          ← Configuração de rotas
│   ├── core/
│   │   └── constants/
│   │       └── app_constants.dart         ← Rotas + URLs
│   ├── models/
│   │   ├── remote_file.dart               ← Arquivo remoto
│   │   ├── app_state.dart                 ← Estado da app
│   │   └── security_question.dart         ← Pergunta de seg.
│   ├── services/
│   │   ├── secure_storage_service.dart    ← Keychain/EncryptedSharedPreferences
│   │   ├── biometric_security_service.dart ← Biometria + Perguntas
│   │   └── api_client_service.dart        ← HTTP + JWT + Retry
│   └── views/
│       ├── splash/
│       │   └── splash_view_new.dart       ← Orquestrador auth
│       ├── pairing/
│       │   └── qr_pairing_view.dart       ← Pareamento QR
│       ├── security/
│       │   └── security_question_view.dart ← Fallback auth
│       └── files/
│           ├── files_list_view.dart       ← Tela principal
│           └── file_preview_modal.dart    ← Preview + download
├── pubspec.yaml                           ← Dependências
└── README.md                              ← Documentação
```

---

## 🚀 Como Executar

### 1. Dependências

```bash
cd mobile
flutter pub get
```

Dependências principais:
- `flutter_secure_storage: ^9.0.0` - Keychain/EncryptedSharedPreferences
- `local_auth: ^2.1.0` - Biometria
- `mobile_scanner: ^6.0.0` - QR scanning
- `uuid: ^4.0.0` - UUID v4
- `intl: ^0.20.2` - Formatação de datas
- `path_provider: ^2.1.0` - Diretórios da app
- `http: ^1.2.0` - HTTP client
- `cached_network_image: ^3.3.0` - Cache de imagens (future)

### 2. Executar

```bash
flutter run -d <device_id>
```

### 3. Fluxo MVP

1. **Primeira vez (sem pareamento)**:
   - App abre → SplashView → detecta não pareado → redireciona para /pairing
   - Usuário tira screenshot do QR ou copia valor JSON
   - Simula escaneamento → salva config → redireciona para /splash

2. **Segunda vez (pareado)**:
   - App abre → SplashView → biometria → sucesso → /files
   - Lista arquivos via GET /api/files
   - Tap em arquivo → preview modal
   - Tap "Baixar" → GET /api/files/{id}/download → salva localmente

---

## 🧪 Testes (TODO)

```bash
flutter test test/
```

Testes sugeridos:
- `test/services/secure_storage_service_test.dart`
- `test/services/biometric_security_service_test.dart`
- `test/services/api_client_service_test.dart`
- `test/views/files_list_view_test.dart`

---

## 📝 Anotações para Próximas Fases

### Phase 2 - Features Adicionais

- [ ] Compartilhamento de arquivos
- [ ] Upload de arquivos novos
- [ ] Sincronização automática
- [ ] Histórico de alterações
- [ ] Backup automático em background

### Phase 3 - Otimizações

- [ ] Cache local de metadados
- [ ] Image thumbnails (cached_network_image)
- [ ] Pagination infinita melhorada
- [ ] Compressão de upload
- [ ] Dark mode (já implementado, só melhorar)

### Phase 4 - Segurança Avançada

- [ ] Encriptação de backup local
- [ ] Two-factor authentication
- [ ] Device trust (reconhecer dispositivos conhecidos)
- [ ] Log de acesso de segurança
- [ ] Biometria em background

---

## ✅ Checklist de Implementação

- [x] SecureStorageService (Keychain/EncryptedSharedPreferences)
- [x] BiometricSecurityService (Fingerprint + Questions + Lockout)
- [x] ApiClientService (HTTP + JWT + Retry)
- [x] SplashViewNew (Orquestrador auth)
- [x] QrPairingView (Pareamento QR)
- [x] SecurityQuestionView (Fallback auth)
- [x] FilesListView (Tela principal OneDrive)
- [x] FilePreviewModal (Preview + download)
- [x] main.dart (Rotas registradas)
- [x] ApiClientService.registerDevice() (Backend pairing)
- [ ] Testes unitários
- [ ] Testes de integração
- [ ] Build release

---

**Status**: ✅ **MVP PRONTO PARA TESTE**

Todos os componentes core estão implementados, testáveis e seguindo padrões de segurança mobile industry-standard.
