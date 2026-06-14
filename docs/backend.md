# Backend Keeply

O backend é uma API REST stateless em Spring Boot. Ele concentra autenticação, autorização, gerenciamento de dispositivos, snapshots, arquivos, chunks, sessões de transferência MinIO, downloads e integração com o Keeply I.A.

## Stack

| Item | Tecnologia |
| --- | --- |
| Linguagem | Java 25 |
| Framework | Spring Boot 4.0.6 |
| Segurança | Spring Security + JWT JJWT |
| Banco | PostgreSQL 16 |
| Migrações | Flyway |
| Storage | MinIO/S3 compatível |
| Compressão | Zstandard via `zstd-jni` |
| Cache/rate limit | Caffeine |
| Métricas | Actuator + Micrometer Prometheus |
| IA | OpenRouter Chat Completions |

## Responsabilidades

- Registrar e autenticar usuários.
- Emitir access token e refresh token.
- Registrar e listar dispositivos.
- Manter plano de proteção por dispositivo.
- Criar, completar, falhar, listar e remover snapshots.
- Validar e persistir manifesto de snapshot.
- Persistir arquivos e relação arquivo/chunks.
- Consultar chunks existentes para deduplicação.
- Emitir credenciais temporárias de upload/restore para MinIO.
- Servir downloads de arquivo, pasta e seleção.
- Expor chat de IA operacional via `/api/ai/chat`.
- Expor health e métricas Prometheus.

## Arquitetura lógica

```mermaid
graph TB
    subgraph Clientes
        AG[Agente JavaFX/Daemon]
        FE[Frontend Next.js]
        MB[Mobile Flutter]
    end

    subgraph Backend Spring Boot
        SEC[SecurityConfig + JwtAuthenticationFilter]
        AUTH[AuthController]
        DEV[DeviceController]
        SNAP[SnapshotController]
        CHUNK[ChunkController]
        TS[TransferSessionController]
        AI[AiController]

        AUTHS[AuthService]
        DEVS[DeviceService]
        SNAPS[SnapshotService]
        MAN[ManifestParserService / ManifestReaderService]
        DOWN[FileDownloadService]
        TRANS[TransferCredentialBroker]
        MINIO[MinioStorageService]
        AIS[AiChatService]
        RATE[RateLimitService]
    end

    PG[(PostgreSQL)]
    S3[(MinIO)]
    OR[OpenRouter]
    PROM[Prometheus]

    AG --> SEC
    FE --> SEC
    MB --> SEC
    SEC --> AUTH & DEV & SNAP & CHUNK & TS & AI
    AUTH --> AUTHS --> RATE
    DEV --> DEVS
    SNAP --> SNAPS --> MAN
    SNAP --> DOWN
    TS --> TRANS
    TRANS --> S3
    MINIO --> S3
    AIS --> OR
    SNAPS --> PG
    MAN --> PG
    AUTH --> PG
    DEV --> PG
    Backend Spring Boot --> PROM
```

## Endpoints principais

### Autenticação

| Método | Rota | Uso |
| --- | --- | --- |
| `POST` | `/api/auth/register` | Cria usuário. |
| `POST` | `/api/auth/login` | Login de usuário. |
| `POST` | `/api/auth/login-device` | Login/registro de dispositivo. |
| `POST` | `/api/auth/refresh` | Renova sessão. |

### Dispositivos

| Método | Rota | Uso |
| --- | --- | --- |
| `POST` | `/api/devices/register` | Registra dispositivo. |
| `GET` | `/api/devices` | Lista dispositivos do usuário. |
| `DELETE` | `/api/devices/{deviceId}` | Remove dispositivo. |
| `PATCH` | `/api/devices/{deviceId}/heartbeat` | Atualiza último sinal de vida. |
| `GET` | `/api/devices/{deviceId}/plan` | Consulta plano de proteção. |
| `PUT` | `/api/devices/{deviceId}/plan` | Atualiza plano de proteção. |

### Snapshots

| Método | Rota | Uso |
| --- | --- | --- |
| `POST` | `/api/snapshots/start` | Inicia snapshot e abre sessão de upload. |
| `POST` | `/api/snapshots/{snapshotId}/complete` | Envia manifesto e finaliza snapshot. |
| `POST` | `/api/snapshots/{snapshotId}/fail` | Marca snapshot como falho. |
| `POST` | `/api/snapshots/{snapshotId}/restore-sessions` | Abre sessão temporária de restore. |
| `GET` | `/api/snapshots` | Lista snapshots. |
| `GET` | `/api/snapshots/{snapshotId}` | Detalha snapshot. |
| `DELETE` | `/api/snapshots/{snapshotId}` | Remove snapshot. |
| `GET` | `/api/snapshots/{snapshotId}/files` | Lista arquivos paginados. |
| `GET` | `/api/snapshots/{snapshotId}/nodes` | Lista árvore/nós de arquivos. |
| `GET` | `/api/snapshots/{snapshotId}/files/download` | Baixa arquivo/pasta. |
| `POST` | `/api/snapshots/{snapshotId}/archive-selected` | Baixa seleção em arquivo compactado. |

### Chunks

| Método | Rota | Uso |
| --- | --- | --- |
| `POST` | `/api/chunks/check` | Verifica chunks já existentes por hash. |
| `GET` | `/api/chunks/storage-usage` | Consulta uso de storage. |

### Sessões de transferência

| Método | Rota | Uso |
| --- | --- | --- |
| `POST` | `/api/transfer-sessions/{id}/renew` | Renova credenciais temporárias. |
| `POST` | `/api/transfer-sessions/{id}/cancel` | Cancela sessão. |
| `POST` | `/api/transfer-sessions/{id}/finish` | Finaliza sessão. |

### IA

| Método | Rota | Uso |
| --- | --- | --- |
| `POST` | `/api/ai/chat` | Envia pergunta e histórico curto para o Keeply I.A. |

Payload:

```json
{
  "message": "Como restauro um arquivo de um snapshot?",
  "history": [
    { "role": "user", "content": "Tenho um backup concluído." },
    { "role": "assistant", "content": "Abra a tela de Backups e selecione o snapshot." }
  ]
}
```

Resposta:

```json
{
  "answer": "Abra Backups, selecione o snapshot desejado, revise o arquivo e baixe para um local seguro antes de substituir o original.",
  "model": "nvidia/nemotron-3-super-120b-a12b:free"
}
```

## Fluxo de snapshot

1. Agente autentica e envia `POST /api/snapshots/start`.
2. Backend cria snapshot com status inicial e uma `transfer_session`.
3. Backend retorna credenciais temporárias MinIO.
4. Agente envia chunks ausentes para staging no MinIO.
5. Agente envia manifesto para `complete`.
6. Backend valida manifesto, promove objetos e persiste metadados.
7. Snapshot passa para `COMPLETED` ou `FAILED`.

## Segurança

- JWT HMAC-SHA256 com segredo em `KEEPLY_JWT_SECRET`.
- Rate limit de login, refresh e downloads via Caffeine.
- CORS configurável em `KEEPLY_ALLOWED_ORIGINS`.
- `KEEPLY_REGISTRATION_CODE` opcional para restringir registro.
- Validação de posse de dispositivo/snapshot por usuário autenticado.
- Credenciais MinIO temporárias por sessão.
- Bloqueios contra path traversal em rotas de download/restauração.
- Prometheus não deve ser exposto publicamente em produção.

## Variáveis importantes

| Variável | Uso |
| --- | --- |
| `SPRING_DATASOURCE_URL` | URL JDBC do PostgreSQL. |
| `SPRING_DATASOURCE_USERNAME` | Usuário do banco. |
| `SPRING_DATASOURCE_PASSWORD` | Senha do banco. |
| `KEEPLY_JWT_SECRET` | Chave de assinatura JWT. |
| `KEEPLY_MASTER_KEY` | Chave mestra do produto. |
| `KEEPLY_MINIO_ENDPOINT` | Endpoint interno MinIO. |
| `KEEPLY_MINIO_ACCESS_KEY` | Access key MinIO. |
| `KEEPLY_MINIO_SECRET_KEY` | Secret key MinIO. |
| `KEEPLY_MINIO_BUCKET` | Bucket de armazenamento. |
| `KEEPLY_ALLOWED_ORIGINS` | Origins permitidas no CORS. |
| `KEEPLY_AI_API_KEY` | Chave OpenRouter para o Keeply I.A. |
| `KEEPLY_AI_MODEL` | Modelo usado pelo chat de IA. |
| `KEEPLY_AI_TIMEOUT_SECONDS` | Timeout da chamada de IA. |

## Observabilidade

```bash
curl -fsS http://localhost:8080/actuator/health
curl -fsS http://localhost:8080/actuator/prometheus
```

No Compose local, Prometheus coleta o backend e o Postgres Exporter. Grafana é provisionado com Prometheus como datasource.

## Limitações atuais

- Rate limit em memória local; não é distribuído entre múltiplas instâncias.
- A IA não possui acesso automático ao banco nem aos snapshots reais; ela responde com base na mensagem e no histórico enviados.
- O pipeline de promoção/validação de chunks ainda pode gerar alto fan-out em snapshots grandes.
- Limpeza de chunks órfãos ainda precisa de rotina de mark-and-sweep.
- Criptografia ponta a ponta dos arquivos ainda não está fechada.
