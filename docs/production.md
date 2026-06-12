# Produção Docker Compose

O compose de produção fica em `infra/docker-compose.prod.yml` e publica somente o Nginx nas portas `80` e `443`.

- `https://keeply.app.br/` serve a landing.
- `https://keeply.app.br/prod` serve o app web.
- `https://keeply.app.br/api` encaminha para o backend Spring.
- `https://keeply.app.br` também encaminha requisições S3 do bucket `keeply` para o MinIO, sem expor o console.
- O endpoint S3 público deve ser `https://keeply.app.br`, sem subpath como `/minio`, porque clientes S3/MinIO rejeitam endpoints com caminho.
- Postgres, backend, frontend, landing e MinIO ficam apenas na rede Docker.

Crie o arquivo de ambiente fora do repositório, por exemplo `/opt/keeply/.env.prod`:

```dotenv
POSTGRES_DB=keeply
POSTGRES_USER=keeply
POSTGRES_PASSWORD=<senha-forte>

MINIO_ROOT_USER=<usuario-forte>
MINIO_ROOT_PASSWORD=<senha-forte>
KEEPLY_MINIO_BUCKET=keeply
KEEPLY_MINIO_PUBLIC_ENDPOINT=https://keeply.app.br

KEEPLY_JWT_SECRET=<32+-chars>
KEEPLY_MASTER_KEY=<64-hex-chars>

NEXT_PUBLIC_API_BASE=https://keeply.app.br
NEXT_BASE_PATH=/prod

KEEPLY_AI_API_KEY=
KEEPLY_AI_BASE_URL=https://openrouter.ai/api/v1
KEEPLY_AI_MODEL=nvidia/nemotron-3-super-120b-a12b:free
KEEPLY_AI_REFERER=https://keeply.app.br
KEEPLY_AI_TITLE=Keeply
```

Os certificados TLS devem existir em `infra/nginx/certs/fullchain.pem` e `infra/nginx/certs/privkey.pem`.

Valide e suba:

```bash
docker compose -f infra/docker-compose.prod.yml --env-file /opt/keeply/.env.prod config
docker compose -f infra/docker-compose.prod.yml --env-file /opt/keeply/.env.prod build
docker compose -f infra/docker-compose.prod.yml --env-file /opt/keeply/.env.prod up -d
```

Checks rápidos:

```bash
curl -I https://keeply.app.br/
curl -I https://keeply.app.br/prod
curl -fsSL https://keeply.app.br/api/actuator/health
curl -fsSL https://keeply.app.br/minio/minio/health/live
```
