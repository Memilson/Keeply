#!/bin/bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
cd "$PROJECT_ROOT"

COMPOSE=(docker compose -f infra/docker-compose.yml)
export DOCKER_BUILDKIT="${DOCKER_BUILDKIT:-1}"
export BUILDKIT_PROGRESS="${BUILDKIT_PROGRESS:-plain}"
export COMPOSE_PROGRESS="${COMPOSE_PROGRESS:-plain}"

NO_CACHE="${KEEPLY_DOCKER_NO_CACHE:-1}"
BUILD_ARGS=()
if [ "$NO_CACHE" = "1" ]; then
    BUILD_ARGS+=(--no-cache)
fi

time_step() {
    local label="$1"
    shift

    echo
    echo "==> $label"
    local start end elapsed
    start=$(date +%s)
    "$@"
    end=$(date +%s)
    elapsed=$((end - start))
    echo "<== $label concluido em ${elapsed}s"
}

echo "Iniciando reset destrutivo do ambiente Keeply para Zstd..."
echo "Este procedimento remove snapshots, chunks, objetos MinIO e caches locais GZIP existentes."
echo "Docker debug ativo: BUILDKIT_PROGRESS=$BUILDKIT_PROGRESS, COMPOSE_PROGRESS=$COMPOSE_PROGRESS"
echo "Rebuild Docker sem cache: $NO_CACHE (use KEEPLY_DOCKER_NO_CACHE=0 para reaproveitar layers)"

# Resolver caminhos XDG dinamicamente
KEEPLY_CONFIG="${XDG_CONFIG_HOME:-$HOME/.config}/keeply"
KEEPLY_DATA="${XDG_DATA_HOME:-$HOME/.local/share}/keeply"
KEEPLY_STATE="${XDG_STATE_HOME:-$HOME/.local/state}/keeply"
KEEPLY_RUNTIME="${XDG_RUNTIME_DIR:-/tmp}/keeply"

# A. Parar todos os processos do agente (Daemon e UI)
echo "Parando processos do agente..."
if [ -f "$KEEPLY_RUNTIME/daemon.pid" ]; then
    DAEMON_PID=$(cat "$KEEPLY_RUNTIME/daemon.pid")
    kill "$DAEMON_PID" 2>/dev/null || true
fi

# Mata por nome de classe principal para ser mais robusto
pkill -f "com.keeply.agent.KeeplyAgentDaemonApp" || true
pkill -f "com.keeply.agent.KeeplyAgentApp" || true
pkill -f "gradle-wrapper.jar :agent:run" || true

# Dá um tempo para os processos liberarem os arquivos
sleep 2

# Força a parada se ainda restarem processos
pkill -9 -f "com.keeply.agent" || true

# 0. Matar o backend se estiver rodando (necessário para o Hibernate recriar as tabelas no novo volume)
echo "Parando processo do backend (Java/Gradle)..."
pkill -f "gradle-wrapper.jar :backend:bootRun" || true
pkill -f "BackendApplication" || true
sleep 1
pkill -9 -f "com.keeply.backend" || true

# 1. Parar infra e remover volumes
echo "Limpando volumes do Docker (Postgres, MinIO, backend e frontend)..."
"${COMPOSE[@]}" down -v --remove-orphans

# 2. Reconstruir e subir tudo com logs detalhados para diagnosticar gargalos.
echo "Reconstruindo imagens Docker (backend e frontend)..."
time_step "docker compose build backend frontend" "${COMPOSE[@]}" build "${BUILD_ARGS[@]}" backend frontend

echo "Subindo infraestrutura limpa..."
time_step "docker compose up" "${COMPOSE[@]}" up -d --force-recreate

# 3. Remover banco local do agente e arquivos de dados
echo "Removendo arquivos de configuracao, estado e bancos do agente..."

# Remover pastas completas do padrão XDG e legado
rm -rf "$KEEPLY_CONFIG"
rm -rf "$KEEPLY_DATA"
rm -rf "$KEEPLY_STATE"
rm -rf "$KEEPLY_RUNTIME"
rm -rf "$HOME/keeply" # Legado

# Limpar eventuais arquivos de banco corrompidos na raiz do projeto (durante testes)
find "$PROJECT_ROOT" -maxdepth 2 -type f -name "*keeply_agent*.db*" -exec rm -f {} +
find "$PROJECT_ROOT" -maxdepth 2 -type f -name "agent.db*" -exec rm -f {} +

echo "Arquivos e diretorios locais limpos."

echo "Aguardando backend (container) ficar online para registrar o usuario..."

# 4. Aguardar o backend estar pronto
MAX_RETRIES=60
COUNT=0
until curl -fsS http://localhost:8080/actuator/health > /dev/null || [ $COUNT -eq $MAX_RETRIES ]; do
    sleep 2
    COUNT=$((COUNT + 1))
    echo "   Tentativa $COUNT/$MAX_RETRIES..."
done

if [ $COUNT -eq $MAX_RETRIES ]; then
    echo "Erro: O backend nao subiu a tempo via Docker Compose."
    exit 1
fi

# 5. Criar usuário de teste
echo "Criando usuario de teste (keeply@keeply.com)..."
HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" -X POST http://localhost:8080/api/auth/register \
     -H "Content-Type: application/json" \
     -d '{"name": "Keeply Test", "email": "keeply@keeply.com", "password": "keeply123"}')

if [ "$HTTP_CODE" = "200" ] || [ "$HTTP_CODE" = "201" ] || [ "$HTTP_CODE" = "409" ]; then
    echo "Usuario pronto (status HTTP $HTTP_CODE)."
else
    echo "Falha ao criar usuario (status HTTP $HTTP_CODE)."
    exit 1
fi

echo -e "\nAmbiente resetado e usuario criado com sucesso!"
