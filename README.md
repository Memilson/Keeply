# Keeply Java 25 Starter

Starter kit do MVP definido na arquitetura:

```text
Agente JavaFX -> Spring Boot Backend -> PostgreSQL + MinIO <- React Dashboard
```

Este pacote entrega a base inicial em Java 25:

- Backend Spring Boot com JWT, usuários, dispositivos, snapshots, chunks e MinIO.
- Agente JavaFX com telas mínimas e núcleo de backup/restore.
- Content-Defined Chunking simplificado.
- SHA-256 por chunk e por arquivo.
- Compressão GZIP.
- Manifesto JSON.
- Docker Compose para PostgreSQL e MinIO.

## Rodando a infra

```bash
cd infra
docker compose up -d
```

MinIO Console:

```text
http://localhost:9001
user: keeply
senha: keeply123
```

PostgreSQL:

```text
localhost:5432
database: keeply
user: keeply
password: keeply
```

## Rodando o backend

```bash
./gradlew :backend:bootRun
```

Backend:

```text
http://localhost:8080
```

## Rodando o agente

```bash
./gradlew :agent:run
```

## Fluxo mínimo de teste

1. Suba PostgreSQL e MinIO.
2. Rode o backend.
3. Rode o agente.
4. Faça register/login pelo endpoint ou ajuste para criar usuário via React depois.
5. Escolha uma pasta no agente.
6. Execute backup.
7. Restaure usando o snapshot gerado.

## Observação

Este é um starter técnico. Ainda faltam:

- React frontend.
- Testes automatizados.
- Retenção automática completa.
- Parser persistente do manifesto em `snapshot_files` e `file_chunks`.
- Criptografia ponta a ponta.
- Endurecimento de segurança.
