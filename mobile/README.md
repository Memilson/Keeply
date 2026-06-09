# 📱 Keeply Mobile - Documentação da Arquitetura

## 🏗️ Arquitetura MVC Implementada

Este projeto utiliza o padrão **MVC (Model-View-Controller)** para separação de responsabilidades:

```
┌─────────────┐     ┌──────────────┐     ┌─────────────┐
│    Model    │ ◄──►│  Controller  │ ◄──►│    View     │
│  (Dados +   │     │ (Lógica +    │     │   (UI +     │
│   Regras)   │     │  Estado)     │     │  Widgets)   │
└─────────────┘     └──────────────┘     └─────────────┘
                           │
                           ▼
                    ┌──────────────┐
                    │   Service    │
                    │ (Supabase +  │
                    │   APIs)      │
                    └──────────────┘
```

## 📁 Estrutura de Diretórios

```
lib/
├── main.dart                     # Ponto de entrada (apenas inicialização)
├── core/                         # Núcleo do aplicativo
│   ├── config/                   # Configurações globais
│   │   └── supabase_config.dart  # Credenciais do Supabase
│   ├── themes/                   # Temas e estilização
│   │   └── app_theme.dart        # Tema Material Design
│   ├── constants/                # Constantes do app
│   │   └── app_constants.dart    # Rotas, strings, configs
│   └── utils/                    # Utilitários
│       └── helpers.dart          # Funções auxiliares
├── models/                       # Modelos de dados
│   └── backup_model.dart         # Modelo de Backup
├── views/                        # Telas e componentes UI
│   ├── splash/                   # Tela de Splash
│   │   └── splash_view.dart
│   └── backups/                  # Telas de Backup
│       └── backup_list_view.dart
├── controllers/                  # Controladores (lógica de negócio)
│   └── backup_controller.dart    # Controller de Backups
└── services/                     # Serviços (integrações externas)
    ├── supabase_service.dart     # Serviço Supabase
    └── backup_service.dart       # Serviço específico de Backups
```

## 🎯 Responsabilidades de Cada Camada

### **Model (models/)**
- Representa os dados do domínio (ex: Backup, Usuário)
- Contém validações e regras de negócio
- Serialização/desserialização (JSON ↔ Dart)
- **NÃO** sabe sobre UI ou Controllers

### **View (views/)**
- Widgets e telas visuais
- Apenas exibe dados e captura interações do usuário
- Delegada toda lógica para o Controller
- **NÃO** acessa Models ou Services diretamente

### **Controller (controllers/)**
- Intermedia View ↔ Model
- Gerencia estado da tela
- Chama Services para buscar/salvar dados
- Notifica Views sobre mudanças (usando `notifyListeners()`)

### **Service (services/)**
- Integrações externas (Supabase, APIs REST)
- Operações de banco de dados
- Autenticação
- **NÃO** sabe sobre UI

## 🔄 Fluxo de Dados Exemplo

```
1. Usuário clica em "Carregar Backups" (View)
   ↓
2. View chama controller.carregarBackups() (Controller)
   ↓
3. Controller chama backupService.buscarTodos() (Service)
   ↓
4. Service consulta Supabase e retorna dados
   ↓
5. Controller converte dados em BackupModel (Model)
   ↓
6. Controller atualiza estado e notifica View
   ↓
7. View rebuilda com nova lista de backups
```

## 🛠️ Como Usar

### 1. Configurar Supabase

Edite `lib/core/config/supabase_config.dart`:

```dart
static const String supabaseUrl = 'https://seu-projeto.supabase.co';
static const String supabaseAnonKey = 'sua-chave-anon-aqui';
```

### 2. Inicializar no main.dart

```dart
void main() async {
  WidgetsFlutterBinding.ensureInitialized();
  await SupabaseService.instance.initialize();
  runApp(const KeeplyApp());
}
```

### 3. Criar um novo Model

```dart
// lib/models/usuario_model.dart
class UsuarioModel {
  final String id;
  final String nome;
  final String email;
  
  const UsuarioModel({
    required this.id,
    required this.nome,
    required this.email,
  });
  
  //fromJson e toJson...
}
```

### 4. Criar um Controller

```dart
// lib/controllers/usuario_controller.dart
class UsuarioController extends ChangeNotifier {
  final SupabaseService _supabase = SupabaseService();
  
  List<UsuarioModel> _usuarios = [];
  List<UsuarioModel> get usuarios => _usuarios;
  
  Future<void> carregarUsuarios() async {
    final data = await _supabase.getAll(table: 'usuarios');
    _usuarios = data.map((e) => UsuarioModel.fromJson(e)).toList();
    notifyListeners(); // Avvisa a View
  }
}
```

### 5. Criar uma View

```dart
// lib/views/usuarios/usuario_list_view.dart
class UsuarioListView extends StatelessWidget {
  @override
  Widget build(BuildContext context) {
    return ChangeNotifierProvider(
      create: (_) => UsuarioController(),
      child: Scaffold(
        body: Consumer<UsuarioController>(
          builder: (context, controller, _) {
            return ListView.builder(
              itemCount: controller.usuarios.length,
              itemBuilder: (context, index) {
                final usuario = controller.usuarios[index];
                return ListTile(title: Text(usuario.nome));
              },
            );
          },
        ),
      ),
    );
  }
}
```

## 📝 Padrões de Código

### Comentários
- Use `///` para documentação de classes e métodos públicos
- Use `//` para comentários internos de implementação
- Explique o **PORQUÊ**, não apenas o **COMO**

### Nomenclatura
- **Models**: `NomeModel` (ex: `BackupModel`, `UsuarioModel`)
- **Views**: `NomeView` (ex: `BackupListView`, `LoginView`)
- **Controllers**: `NomeController` (ex: `BackupController`)
- **Services**: `NomeService` (ex: `SupabaseService`, `AuthService`)

### Gerenciamento de Estado
- Use `ChangeNotifier` + `Provider` para estado simples
- Considere `Riverpod` ou `Bloc` para apps mais complexos

## 🔐 Segurança

- **NUNCA** commit credenciais reais no Git
- Use variáveis de ambiente em produção
- Configure Row Level Security (RLS) no Supabase
- Valide dados no client E no server

## 🚀 Próximos Passos

1. Configurar tabelas no Supabase
2. Implementar autenticação
3. Criar telas de CRUD de Backups
4. Adicionar testes unitários
5. Implementar offline-first com Hive
