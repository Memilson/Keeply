#!/bin/bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
cd "$PROJECT_ROOT"

echo "🚀 Iniciando reset do ambiente Keeply..."

# A. Parar daemon do agente
echo "🛑 Parando daemon do agente..."
if [ -f "/home/angelo/keeply/daemon.pid" ]; then
    kill "$(cat /home/angelo/keeply/daemon.pid)" || true
fi
pkill -f "com.keeply.agent.KeeplyAgentDaemonApp" || true
rm -f "/home/angelo/keeply/daemon.pid"

# 0. Matar o backend se estiver rodando (necessário para o Hibernate recriar as tabelas no novo volume)
echo "🛑 Parando processo do backend (Java/Gradle)..."
pkill -f "gradle-wrapper.jar :backend:bootRun" || true
pkill -f "BackendApplication" || true

# 1. Parar infra e remover volumes
echo "📦 Limpando volumes do Docker (Postgres e MinIO)..."
docker compose -f infra/docker-compose.yml down -v

# 2. Subir infra limpa
echo "⚡ Subindo infraestrutura..."
docker compose -f infra/docker-compose.yml up -d

# 3. Remover banco local do agente e arquivos de dados
echo "💾 Removendo banco SQLite local e arquivos de dados..."
AGENT_DATA_DIR="/home/angelo/keeply"
SQLITE_FILES=(
  "${PROJECT_ROOT}/keeply_agent.db"
  "${PROJECT_ROOT}/keeply_agent.db-wal"
  "${PROJECT_ROOT}/keeply_agent.db-shm"
  "${PROJECT_ROOT}/agent/keeply_agent.db"
  "${PROJECT_ROOT}/agent/keeply_agent.db-wal"
  "${PROJECT_ROOT}/agent/keeply_agent.db-shm"
  "${AGENT_DATA_DIR}/agent.db"
  "${AGENT_DATA_DIR}/agent.db-wal"
  "${AGENT_DATA_DIR}/agent.db-shm"
  "${AGENT_DATA_DIR}/keeply_agent_ui.db"
  "${AGENT_DATA_DIR}/daemon.log"
  "${AGENT_DATA_DIR}/device-auth.json"
  "${AGENT_DATA_DIR}/device-id.txt"
)
for file in "${SQLITE_FILES[@]}"; do
    if [ -f "$file" ]; then
        rm -f "$file"
        echo "   removido: $file"
    fi
done

if [ -f "${AGENT_DATA_DIR}/agent.db" ]; then
    echo "❌ Erro: não foi possível remover todos os SQLite do agente em $AGENT_DATA_DIR"
    exit 1
fi
echo "✅ SQLite e logs locais limpos."

echo "⚠️  AVISO: Por favor, inicie o backend agora (./gradlew :backend:bootRun) em outro terminal."
echo "⏳ Aguardando backend (port 8080) ficar online para registrar o usuário..."

# 4. Aguardar o backend estar pronto
MAX_RETRIES=60
COUNT=0
until curl -s http://localhost:8080/actuator/health > /dev/null || [ $COUNT -eq $MAX_RETRIES ]; do
    sleep 2
    COUNT=$((COUNT + 1))
    echo "   Tentativa $COUNT/$MAX_RETRIES..."
done

if [ $COUNT -eq $MAX_RETRIES ]; then
    echo "❌ Erro: O backend não subiu a tempo ou não foi iniciado."
    exit 1
fi

# 5. Criar usuário de teste
echo "👤 Criando usuário de teste (keeply@keeply.com)..."
curl -s -X POST http://localhost:8080/api/auth/register \
     -H "Content-Type: application/json" \
     -d '{"name": "Keeply Test", "email": "keeply@keeply.com", "password": "keeply123"}'

echo -e "\n✅ Ambiente resetado e usuário criado com sucesso!"
