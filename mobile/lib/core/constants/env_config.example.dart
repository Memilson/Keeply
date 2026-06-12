// CÓPIA DE EXEMPLO PARA DESENVOLVIMENTO LOCAL
// O app usa https://keeply.app.br por padrão.
// Para override em build/dev, use:
// flutter run --dart-define=KEEPLY_BACKEND_BASE_URL=http://10.0.2.2:8080

class EnvConfig {
  // Emulador Android: http://10.0.2.2:8080
  // Celular físico: http://IP_DA_SUA_MAQUINA_NA_REDE:8080
  // Produção: https://keeply.app.br
  static const String backendBaseUrl = 'https://keeply.app.br';
}
