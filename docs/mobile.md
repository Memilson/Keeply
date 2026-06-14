# Mobile Keeply

O aplicativo mobile é um cliente Flutter para consultar e operar partes do ambiente Keeply a partir do celular. Ele não substitui o agente desktop: o backup continua sendo executado pelo agente na máquina protegida.

## Objetivo

- Autenticar o usuário.
- Consultar dispositivos vinculados.
- Listar snapshots.
- Buscar arquivos dentro de backups.
- Visualizar detalhes de snapshots.
- Baixar/abrir arquivos quando suportado.
- Gerenciar configurações básicas do app.

## Stack

| Item | Tecnologia |
| --- | --- |
| Framework | Flutter |
| Estado | Provider |
| HTTP | `http` |
| Sessão | `flutter_secure_storage` e serviços locais |
| Arquivos | `file_picker`, `path_provider` |
| Permissões | `permission_handler` |
| Segurança local | `local_auth`, `encrypt`, `crypto` |

## Estrutura

```text
mobile/lib
├── controllers/     # Auth, dashboard, arquivos e configurações
├── core/            # constantes, tema e configuração
├── models/          # entidades do app
├── services/        # API, storage, rede, download, permissões
├── views/           # telas
└── widgets/         # componentes reutilizáveis
```

## Backend configurável

O app usa `https://keeply.app.br` por padrão:

```dart
static const String defaultBackendBaseUrl = String.fromEnvironment(
  'KEEPLY_BACKEND_BASE_URL',
  defaultValue: 'https://keeply.app.br',
);
```

Para desenvolvimento local:

```bash
cd mobile
flutter run --dart-define=KEEPLY_BACKEND_BASE_URL=http://10.0.2.2:8080
```

No emulador Android, `10.0.2.2` aponta para o host. Em celular físico, use o IP da máquina na rede:

```bash
flutter run --dart-define=KEEPLY_BACKEND_BASE_URL=http://192.168.1.50:8080
```

Não use `localhost` no celular físico, porque ele aponta para o próprio aparelho.

## Telas principais

| Tela | Função |
| --- | --- |
| `SplashView` | Inicialização e verificação de estado. |
| `LoginView` | Autenticação. |
| `MainShell` | Estrutura principal de navegação. |
| `FilesListView` | Lista/busca arquivos disponíveis. |
| `SnapshotDetailsView` | Detalhes de snapshot. |
| `FileDownloadView` | Download de arquivo. |
| `SettingsView` | Configurações. |
| `SecurityQuestionView` | Fluxo local de segurança. |

## Busca profunda

A busca profunda não varre arquivos localmente no celular. Ela chama o backend para consultar arquivos indexados nos snapshots.

Fluxo:

1. Usuário digita termo de busca.
2. `FilesController` chama serviço de API.
3. Backend consulta `snapshot_files` e/ou manifestos persistidos.
4. Resultados são exibidos no app.

## Endpoint de IA

O mobile possui constante para `/api/ai/chat`, mas neste pacote a interface completa do chat está implementada no frontend web. Portanto, para a atividade N2, a demonstração mais segura é pelo painel web.

## Build Android

```bash
cd mobile
flutter pub get
flutter build apk --release
```

APK gerado:

```text
mobile/build/app/outputs/flutter-apk/app-release.apk
```

## Limitações atuais

- O app depende do backend estar acessível na rede.
- O backup em si continua no agente desktop.
- O chat de IA não está exposto como tela completa no mobile neste pacote.
- Downloads dependem de permissões do Android e do suporte do aparelho para abrir o tipo de arquivo.
