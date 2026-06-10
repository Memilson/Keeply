# Keeply Mobile App

## Arquitetura (MVC)
O aplicativo mobile foi refatorado para utilizar uma arquitetura **MVC (Model-View-Controller)** limpa, separando a lógica de negócio da interface do usuário. A injeção de dependências e a gerência de estado são feitas através do pacote `provider`.

### Estrutura de Pastas
- `lib/models/`: Classes de dados e DTOs (ex: `RemoteFile`, `DeviceDetails`).
- `lib/controllers/`: Controladores (`ChangeNotifier`) que concentram a lógica de negócio.
  - `auth_controller.dart`: Autenticação, biometria e gerenciamento de sessão.
  - `files_controller.dart`: Listagem de snapshots, busca profunda (deep search) e downloads.
  - `settings_controller.dart`: Gerenciamento de configurações locais e permissões.
- `lib/views/`: Componentes visuais puramente declarativos (UI).
- `lib/services/`: Serviços que interagem com o sistema operacional ou backend (ex: `api_client_service.dart`, `secure_storage_service.dart`).

## Fluxo de Autenticação
O `SplashView` utiliza o `AuthController` para verificar se existe um token JWT válido salvo no `SecureStorage`. Se existir, o usuário é redirecionado para a tela principal (`MainShell`). Caso contrário, é levado para o `LoginView`.

## Busca Profunda (Deep Search)
Diferente da busca tradicional que filtra apenas os nomes dos snapshots, a Busca Profunda (`FilesController.performDeepSearch`) faz requisições ao backend para buscar metadados de arquivos *dentro* de todos os backups. Os resultados são exibidos em uma lista consolidada no `FilesListView`.

## Dependências Principais
- `provider`: Gerenciamento de estado (MVC).
- `flutter_secure_storage`: Armazenamento seguro de tokens e credenciais.
- `file_picker`: Seleção de diretórios customizados para salvar downloads.
- `permission_handler`: Gerenciamento de permissões do Android/iOS.
