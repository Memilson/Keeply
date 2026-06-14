# Chamadas úteis com curl

Este arquivo reúne chamadas mínimas para testar a API local. Pressupõe backend em `http://localhost:8080`.

## Health

```bash
curl -fsS http://localhost:8080/actuator/health
```

## Registrar usuário

```bash
curl -sS -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Keeply Test",
    "email": "keeply@keeply.com",
    "password": "keeply123"
  }'
```

## Login

```bash
TOKEN=$(curl -sS -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"keeply@keeply.com","password":"keeply123"}' \
  | jq -r .accessToken)

echo "$TOKEN"
```

## Listar dispositivos

```bash
curl -sS http://localhost:8080/api/devices \
  -H "Authorization: Bearer $TOKEN" | jq
```

## Registrar dispositivo

```bash
curl -sS -X POST http://localhost:8080/api/devices/register \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Notebook Dev",
    "hostname": "dev-machine",
    "osName": "Linux",
    "deviceInstallationId": "dev-installation-001",
    "agentVersion": "0.1.0"
  }' | jq
```

## Listar snapshots

```bash
curl -sS http://localhost:8080/api/snapshots \
  -H "Authorization: Bearer $TOKEN" | jq
```

## Listar arquivos de um snapshot

```bash
SNAPSHOT_ID="coloque-o-uuid-aqui"

curl -sS "http://localhost:8080/api/snapshots/$SNAPSHOT_ID/files?page=0&size=20&search=doc" \
  -H "Authorization: Bearer $TOKEN" | jq
```

## Baixar arquivo de um snapshot

```bash
SNAPSHOT_ID="coloque-o-uuid-aqui"
FILE_PATH="Documents/exemplo.txt"

curl -L -o exemplo.txt \
  "http://localhost:8080/api/snapshots/$SNAPSHOT_ID/files/download?path=$FILE_PATH" \
  -H "Authorization: Bearer $TOKEN"
```

## Testar Keeply I.A

Requer `KEEPLY_AI_API_KEY` configurada no backend.

```bash
curl -sS -X POST http://localhost:8080/api/ai/chat \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "message": "Como verifico se meus backups estão saudáveis?",
    "history": []
  }' | jq
```

## Métricas Prometheus

```bash
curl -fsS http://localhost:8080/actuator/prometheus | head
```

## Observações

- Todas as rotas de domínio exigem JWT, exceto autenticação e health.
- Use `jq` apenas para facilitar leitura; a API não depende dele.
- Em produção, troque `http://localhost:8080` por `https://keeply.app.br/api` quando a rota estiver atrás do Nginx.
