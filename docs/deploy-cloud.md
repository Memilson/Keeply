# Plano de Deploy Cloud do Keeply

## Resumo

O deploy de produção deve rodar em um único VPS com Docker Compose, Nginx containerizado e Cloudflare na frente dos domínios públicos.

O Nginx será o único serviço exposto nas portas `80` e `443`. Frontend, backend, MinIO e Postgres ficam em rede Docker interna. O Postgres não precisa de DNS público para a aplicação funcionar: frontend e agente falam com o backend, e o backend grava no banco.

Prometheus roda no mesmo host e na mesma rede Docker, mas com a UI publicada apenas em `127.0.0.1:${PROMETHEUS_PORT:-9090}` para inspeção local, túnel SSH ou acesso operacional controlado.
Grafana segue a mesma estratégia e fica publicado apenas em `127.0.0.1:${GRAFANA_PORT:-3001}` em produção.

## DNS

Configuração desejada:

| DNS | Destino | Cloudflare | Uso |
| --- | --- | --- | --- |
| `keeply.app.br` | IP do VPS | Proxy ligado | Frontend |
| `backend.keeply.app.br` | IP do VPS | Proxy ligado | API Spring Boot |
| `minio.keeply.app.br` | IP do VPS | DNS-only | MinIO S3 API |
| `db.keeply.app.br` | reservado | DNS-only ou não criado | Sem exposição pública |

`minio.keeply.app.br` deve ficar em DNS-only para evitar limites e timeouts do proxy Cloudflare em uploads S3 de backup.

## Arquitetura Docker

Criar um compose de produção, por exemplo `infra/docker-compose.prod.yml`, com estes serviços:

- `nginx`: publica `80:80` e `443:443`.
- `nginx-exporter`: coleta métricas do Nginx por endpoint interno `nginx:8081/nginx_status`.
- `frontend`: expõe apenas `3000` na rede Docker.
- `backend`: expõe apenas `8080` na rede Docker.
- `minio`: expõe apenas `9000` na rede Docker; console `9001` não público.
- `postgres`: expõe apenas `5432` na rede Docker.
- `postgres-exporter`: expõe métricas do banco para o Prometheus dentro da rede Docker.
- `prometheus`: scrappeia `backend`, `postgres-exporter` e `nginx-exporter` por hostname interno.
- `grafana`: consome o Prometheus internamente e publica a UI só em loopback.

O fluxo de dados fica:

```text
Browser -> https://keeply.app.br -> nginx -> frontend:3000
Frontend -> https://backend.keeply.app.br -> nginx -> backend:8080
Agente -> https://backend.keeply.app.br -> nginx -> backend:8080
Agente -> https://minio.keeply.app.br -> nginx -> minio:9000
Backend -> postgres:5432
Backend -> minio:9000
Prometheus -> backend:8080/actuator/prometheus
Prometheus -> postgres-exporter:9187
Prometheus -> nginx-exporter:9113
Grafana -> prometheus:9090
```

## Observabilidade

Arquivos centrais desta v1:

- `infra/docker-compose.yml`: stack local com Prometheus e Grafana configurados inline no próprio compose.
- `infra/docker-compose.prod.yml`: stack de produção com Nginx, Prometheus e Grafana configurados inline no próprio compose.
- `infra/nginx/certs/`: diretório reservado apenas para certificados de produção.

Escopo fechado da v1:

- `backend`: incluído via `GET /actuator/prometheus`.
- `postgres`: incluído via `postgres-exporter`.
- `nginx`: incluído apenas na produção containerizada, via `nginx-exporter`.
- `minio`: adiado nesta v1. A imagem atual não entrou no scrape até validar endpoint/exporter estável sem ampliar superfície de exposição.

## Nginx

Configurar virtual hosts:

- `keeply.app.br` proxy para `http://frontend:3000`.
- `backend.keeply.app.br` proxy para `http://backend:8080`.
- `minio.keeply.app.br` proxy para `http://minio:9000`.

Headers comuns:

```nginx
proxy_set_header Host $host;
proxy_set_header X-Real-IP $remote_addr;
proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
proxy_set_header X-Forwarded-Proto $scheme;
```

Para MinIO:

```nginx
client_max_body_size 0;
proxy_request_buffering off;
proxy_buffering off;
proxy_read_timeout 3600s;
proxy_send_timeout 3600s;
```

## TLS

Usar Cloudflare com SSL/TLS em `Full (strict)`.

Como `minio.keeply.app.br` ficará DNS-only, o VPS precisa ter certificado público válido para esse domínio. A opção recomendada é emitir Let's Encrypt via DNS-01 usando token da Cloudflare.

Não commitar tokens Cloudflare, chaves privadas ou certificados no repositório.

## Variáveis de Produção

Criar um `.env.prod` fora do repositório ou em local seguro no servidor.

Exemplo sem secrets reais:

```dotenv
POSTGRES_DB=keeply
POSTGRES_USER=keeply
POSTGRES_PASSWORD=<senha-forte>
GRAFANA_ADMIN_USER=<usuario-admin>
GRAFANA_ADMIN_PASSWORD=<senha-admin-forte>

MINIO_ROOT_USER=<usuario-forte>
MINIO_ROOT_PASSWORD=<senha-forte>
KEEPLY_MINIO_BUCKET=keeply

SPRING_DATASOURCE_URL=jdbc:postgresql://postgres:5432/keeply
SPRING_DATASOURCE_USERNAME=keeply
SPRING_DATASOURCE_PASSWORD=<senha-forte>

KEEPLY_MINIO_ENDPOINT=http://minio:9000
KEEPLY_MINIO_PUBLIC_ENDPOINT=https://minio.keeply.app.br
KEEPLY_MINIO_ACCESS_KEY=<usuario-forte>
KEEPLY_MINIO_SECRET_KEY=<senha-forte>

KEEPLY_JWT_SECRET=<secret-32+-chars>
KEEPLY_MASTER_KEY=<hex-64-chars>
KEEPLY_TRUST_PROXY_HEADERS=true
KEEPLY_CORS_ALLOWED_ORIGIN_PATTERNS=https://keeply.app.br

NEXT_PUBLIC_API_BASE=https://backend.keeply.app.br
```

## Ajustes Necessários no App

Backend:

- Tornar CORS configurável por env.
- Permitir `https://keeply.app.br` em produção.
- Confiar nos headers do Nginx para IP real quando `KEEPLY_TRUST_PROXY_HEADERS=true`.
- Usar `KEEPLY_MINIO_ENDPOINT=http://minio:9000` internamente.
- Usar `KEEPLY_MINIO_PUBLIC_ENDPOINT=https://minio.keeply.app.br` nas credenciais temporárias.
- Expor `GET /actuator/prometheus` sem JWT apenas para scrape interno pela rede Docker.

Frontend:

- Buildar com `NEXT_PUBLIC_API_BASE=https://backend.keeply.app.br`.
- Evitar textos hardcoded com `http://localhost:8080` em telas de produção.

Postgres:

- Não publicar porta `5432` no host.
- Manter acesso somente via rede Docker pelo hostname `postgres`.

MinIO:

- Publicar apenas a API S3 em `minio.keeply.app.br`.
- Não expor o console na internet.
- Acessar console/admin por SSH tunnel ou `docker exec` quando necessário.

## Firewall

Liberar publicamente:

- `80/tcp`
- `443/tcp`

Não liberar publicamente:

- `3000/tcp`
- `8080/tcp`
- `5432/tcp`
- `9000/tcp`
- `9001/tcp`
- `9090/tcp` em interface pública
- `3000/tcp` do Grafana em interface pública
- `9187/tcp`
- `9113/tcp`

O acesso externo a MinIO deve passar por `443` via Nginx, não pela porta `9000` direta.
`/actuator/prometheus` também não deve ganhar rota pública no Nginx.

## Checklist de Validação

Validar compose:

```bash
docker compose -f infra/docker-compose.yml config
docker compose -f infra/docker-compose.prod.yml config
```

Subir produção:

```bash
docker compose -f infra/docker-compose.prod.yml --env-file /caminho/seguro/.env.prod up -d --build
```

Health checks:

```bash
curl -fsSL https://backend.keeply.app.br/actuator/health
curl -fsSL https://minio.keeply.app.br/minio/health/live
curl -fsSL http://127.0.0.1:9090/-/healthy
curl -fsSL http://127.0.0.1:3000/api/health
```

Validação de targets:

```bash
curl -fsSL http://127.0.0.1:9090/api/v1/targets
curl -fsSL http://127.0.0.1:9090/api/v1/query --data-urlencode 'query=up'
```

Critérios de aceite:

- `https://keeply.app.br` abre o frontend.
- Login/register chamam `https://backend.keeply.app.br` sem erro de CORS.
- Backend retorna health `UP`.
- `https://backend.keeply.app.br/actuator/prometheus` não deve estar publicado externamente.
- O target `backend` aparece como `UP` no Prometheus.
- O target `postgres-exporter` aparece como `UP` no Prometheus.
- Em produção containerizada, o target `nginx-exporter` aparece como `UP`.
- O Grafana abre em loopback e já enxerga o datasource `Prometheus` sem configuração manual.
- Agente autentica no backend público.
- Agente recebe endpoint MinIO público como `https://minio.keeply.app.br`.
- Backup envia chunks para MinIO.
- Backend grava metadados no Postgres interno.
- Restore/download lê objetos do MinIO.
- `IP_DO_VPS:5432`, `:9000`, `:9001`, `:9090`, `:9187`, `:9113`, `:8080`, `:3000` do frontend e `:3000` ou porta definida do Grafana não respondem publicamente.

## Decisões Fechadas

- Um VPS Docker com um IP público.
- Nginx rodando como container.
- Cloudflare proxy para frontend e backend.
- MinIO em DNS-only.
- Postgres privado, sem exposição pública.
- `db.keeply.app.br` reservado para uso futuro, mas sem abrir porta.
- Console MinIO fora da internet nesta primeira versão.
