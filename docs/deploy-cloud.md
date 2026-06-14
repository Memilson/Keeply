# Deploy em nuvem

Este documento descreve uma implantação prática do Keeply em uma VM Linux usando Docker Compose. Para produção formal, leia também `docs/production.md`.

## Arquitetura recomendada

```text
Internet
  |
  v
Nginx / TLS
  |
  +--> landing Next.js
  +--> frontend Next.js
  +--> backend Spring Boot
            |
            +--> PostgreSQL
            +--> MinIO
            +--> OpenRouter para Keeply I.A
```

## Requisitos da VM

Mínimo para demonstração:

- 2 vCPU.
- 4 GB RAM.
- 40 GB SSD.
- Ubuntu Server LTS ou Debian estável.
- Docker Engine e Docker Compose plugin.
- Domínio apontando para o IP público.
- Certificado TLS.

Para uso real, aumente disco e memória conforme retenção de backups.

## Preparação do servidor

```bash
sudo apt update
sudo apt install -y ca-certificates curl gnupg git ufw

sudo install -m 0755 -d /etc/apt/keyrings
curl -fsSL https://download.docker.com/linux/ubuntu/gpg \
  | sudo gpg --dearmor -o /etc/apt/keyrings/docker.gpg
sudo chmod a+r /etc/apt/keyrings/docker.gpg

echo \
  "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] https://download.docker.com/linux/ubuntu \
  $(. /etc/os-release && echo "$VERSION_CODENAME") stable" \
  | sudo tee /etc/apt/sources.list.d/docker.list > /dev/null

sudo apt update
sudo apt install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
sudo usermod -aG docker $USER
```

Faça logout/login após adicionar o usuário ao grupo `docker`.

## Firewall

```bash
sudo ufw allow OpenSSH
sudo ufw allow 80/tcp
sudo ufw allow 443/tcp
sudo ufw enable
sudo ufw status
```

Não exponha PostgreSQL, MinIO Console, Prometheus ou Grafana diretamente na Internet.

## Deploy do projeto

```bash
git clone <url-do-repositorio> keeply
cd keeply
sudo mkdir -p /opt/keeply
sudo cp .env.example /opt/keeply/.env.prod
sudo nano /opt/keeply/.env.prod
```

Preencha as variáveis reais, principalmente:

- `POSTGRES_PASSWORD`
- `MINIO_ROOT_PASSWORD`
- `KEEPLY_JWT_SECRET`
- `KEEPLY_MASTER_KEY`
- `KEEPLY_ALLOWED_ORIGINS`
- `NEXT_PUBLIC_API_BASE`
- `KEEPLY_AI_API_KEY`, caso o Keeply I.A vá ser usado

Suba:

```bash
docker compose -f infra/docker-compose.prod.yml --env-file /opt/keeply/.env.prod up -d --build
```

## DNS

Crie registro `A`:

```text
keeply.app.br -> IP_DA_VM
```

Aguarde propagação antes de emitir TLS.

## TLS

O Compose espera:

```text
infra/nginx/certs/fullchain.pem
infra/nginx/certs/privkey.pem
```

Pode usar Certbot no host e copiar/sincronizar os arquivos para esse caminho, ou ajustar o volume do Compose para apontar para o diretório real do certificado.

## Validação

```bash
curl -I https://keeply.app.br/
curl -I https://keeply.app.br/prod
curl -fsS https://keeply.app.br/api/actuator/health
```

Teste funcional:

1. Criar usuário.
2. Login no painel.
3. Abrir Keeply I.A.
4. Registrar agente apontando para `https://keeply.app.br`.
5. Executar backup pequeno.
6. Conferir snapshot.
7. Baixar arquivo de teste.

## Atualização

```bash
cd keeply
git pull

docker compose -f infra/docker-compose.prod.yml --env-file /opt/keeply/.env.prod build

docker compose -f infra/docker-compose.prod.yml --env-file /opt/keeply/.env.prod up -d
```

## Backup da infraestrutura

Backup mínimo:

- volume do PostgreSQL;
- volume do MinIO;
- `/opt/keeply/.env.prod`;
- certificados TLS;
- versão/tag do código implantado.

Sem backup do PostgreSQL e MinIO juntos, a restauração do produto pode ficar inconsistente.

## Limitações desta abordagem

- Compose em VM única não é alta disponibilidade.
- Rate limit do backend é local em memória.
- MinIO em volume local precisa de política clara de backup.
- Escala horizontal exige storage, banco e sessão/rate limit mais bem planejados.
- A IA depende da disponibilidade do provedor externo configurado.
