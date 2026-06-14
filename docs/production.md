# Produção com Docker Compose

A produção usa `infra/docker-compose.prod.yml`. O Nginx é o único serviço publicado diretamente nas portas `80` e `443`.

## Rotas públicas

| Rota | Destino |
| --- | --- |
| `https://keeply.app.br/` | Landing page. |
| `https://keeply.app.br/prod` | Painel web. Redireciona para login. |
| `https://keeply.app.br/api` | Backend Spring Boot. |
| `https://keeply.app.br/api/actuator/health` | Health do backend. |
| `https://keeply.app.br/minio/` | Proxy para MinIO conforme Compose atual. |

O endpoint Prometheus é bloqueado no Nginx de produção.

## Arquivo de ambiente

Crie um arquivo fora do repositório, por exemplo:

```bash
sudo mkdir -p /opt/keeply
sudo nano /opt/keeply/.env.prod
```

Exemplo:

```dotenv
POSTGRES_DB=keeply
POSTGRES_USER=keeply_prod
POSTGRES_PASSWORD=<senha-forte>

MINIO_ROOT_USER=<usuario-forte>
MINIO_ROOT_PASSWORD=<senha-forte>
KEEPLY_MINIO_BUCKET=keeply
KEEPLY_MINIO_PUBLIC_ENDPOINT=https://keeply.app.br

KEEPLY_JWT_SECRET=<32+-caracteres>
KEEPLY_MASTER_KEY=<64-hex-caracteres>
KEEPLY_REGISTRATION_CODE=<codigo-opcional>
KEEPLY_ALLOWED_ORIGINS=https://keeply.app.br
KEEPLY_TRUST_PROXY_HEADERS=true

NEXT_PUBLIC_API_BASE=https://keeply.app.br
NEXT_BASE_PATH=/prod

KEEPLY_AI_BASE_URL=https://openrouter.ai/api/v1
KEEPLY_AI_MODEL=nvidia/nemotron-3-super-120b-a12b:free
KEEPLY_AI_API_KEY=<chave-openrouter>
KEEPLY_AI_TITLE=Keeply
KEEPLY_AI_REFERER=https://keeply.app.br
KEEPLY_AI_TIMEOUT_SECONDS=60
```

## Certificados TLS

O Compose espera certificados em:

```text
infra/nginx/certs/fullchain.pem
infra/nginx/certs/privkey.pem
```

Esses arquivos não devem ser versionados.

## Subir produção

A partir da raiz do projeto:

```bash
docker compose -f infra/docker-compose.prod.yml --env-file /opt/keeply/.env.prod config

docker compose -f infra/docker-compose.prod.yml --env-file /opt/keeply/.env.prod build

docker compose -f infra/docker-compose.prod.yml --env-file /opt/keeply/.env.prod up -d
```

## Checks

```bash
curl -I https://keeply.app.br/
curl -I https://keeply.app.br/prod
curl -fsS https://keeply.app.br/api/actuator/health
```

Logs:

```bash
docker compose -f infra/docker-compose.prod.yml --env-file /opt/keeply/.env.prod logs -f backend

docker compose -f infra/docker-compose.prod.yml --env-file /opt/keeply/.env.prod logs -f nginx
```

## Recursos e limites

O Compose define limites básicos:

| Serviço | Limite atual |
| --- | --- |
| `backend` | 1 GB |
| `frontend` | 512 MB |
| `landing` | 512 MB |
| `postgres` | 1 GB |

Ajuste conforme volume real de snapshots e concorrência.

## Cuidados de produção

- Trocar todas as senhas padrão.
- Usar secrets fortes para JWT e master key.
- Fazer backup do volume do PostgreSQL e do MinIO.
- Validar restore periodicamente.
- Não expor MinIO Console publicamente.
- Bloquear Prometheus/Grafana fora da rede administrativa.
- Monitorar uso de disco do MinIO.
- Validar o comportamento do proxy S3 se alterar paths do Nginx.
- Definir política de retenção e limpeza de chunks órfãos antes de uso real.
