// CÓPIA DE EXEMPLO
// Renomeie este arquivo para env_config.dart e preencha com o IP da sua máquina.

class EnvConfig {
  // Substitua 'SEU_IP' pelo IP real do seu servidor de backend/agent local.
  // Exemplo: 'http://192.168.0.10:8080'
  static const String backendBaseUrl = 'http://SEU_IP:8080';
  
  // Exemplo: 'ws://192.168.0.10:8080/ws/agent'
  static const String wsDefaultUrl = 'ws://SEU_IP:8080/ws/agent';
}
