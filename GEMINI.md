# Keeply: Architectural Mandates & Domain Knowledge

Esta documentação serve como o guia mestre para qualquer agente (humano ou IA) que trabalhe no Keeply. Ela define os padrões inegociáveis de performance, segurança e arquitetura.

## Core Mandates (Mandatos Inegociáveis)

1.  **Streaming Pipeline Above All:** Nunca carregue listas completas de caminhos de arquivos ou payloads de chunks em memória. Use `java.util.stream.Stream` e processamento por demanda para garantir consumo de memória constante (O(1) em relação ao tamanho do backup).
2.  **Deduplication Integrity:** Toda alteração no `ContentDefinedChunker.java` deve ser validada contra a suíte de testes. A quebra de boundaries de chunks invalida o cache global e causa re-uploads em massa.
3.  **Thread-Safe Authentication:** O `BackendClient` deve gerenciar a renovação de tokens (Refresh Token) de forma sincronizada para suportar múltiplos uploads paralelos sem colisões de 401 Unauthorized.
4.  **Database Efficiency:** Use conexões persistentes e transações em lote no `LocalDatabase` (SQLite). Evite abrir/fechar conexões em loops de processamento de arquivos. Ative `PRAGMA journal_mode=WAL` para performance.

## Domain Dictionary (Dicionário de Domínio)

-   **CDC (Content-Defined Chunking):** Algoritmo que divide arquivos em pedaços baseando-se no conteúdo (rolling hash) em vez de tamanhos fixos. Permite deduplicação eficiente mesmo com inserções/remoções no meio do arquivo.
-   **Chunk:** Um pedaço de dado comprimido e hasheado (SHA-256). É a unidade básica de armazenamento no MinIO.
-   **Manifest:** Arquivo JSON que descreve a estrutura de um Snapshot, listando caminhos de arquivos e seus respectivos hashes de chunks.
-   **Snapshot:** Uma versão completa de uma origem de backup em um determinado ponto no tempo.
-   **Daemon:** Processo de background do agente que executa os ciclos de backup agendados.

## Technology Stack

-   **Agent:** Java 25 (Starter), JavaFX (UI), SQLite (Local Cache).
-   **Backend:** Spring Boot 3+, PostgreSQL (Metadata), MinIO (Object Storage).
-   **Security:** JWT com rotação de Refresh Tokens e Device Identity persistente.

## Development Workflow

-   **Reset Ambiente:** Use `./debug/reset_env.sh` para limpar banco, logs e volumes docker.
-   **Build:** `./gradlew classes` (validação rápida) ou `./gradlew build` (full).
-   **Logs do Daemon:** Localizados em `~/keeply/daemon.log`.

---
*Nota: Se você for um agente de IA, carregue estas instruções em todas as sessões para garantir que suas sugestões de código não quebrem a pipeline de streaming.*
