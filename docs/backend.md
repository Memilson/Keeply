# Arquitetura do Backend

## Escopo

O backend (`backend/`) é Spring Boot com REST, JWT, JPA/Flyway e serviços de snapshot/chunk/transferência.

## Componentes principais

- `controller/`: endpoints de auth, device, snapshot, chunk e transfer session.
- `service/`: regras de negócio e integração com storage.
- `security/`: filtro JWT e principal autenticado.
- `repository/` + `model/`: persistência e domínio.
- `ManifestParserService`: auditoria do manifesto e promoção de objetos.

## Fluxo resumido de backup

1. `startSnapshot` cria snapshot `IN_PROGRESS` e `transfer_session`.
2. Agente envia chunks/manifesto para staging no MinIO.
3. `completeSnapshot` muda para `PROCESSING`.
4. `ManifestParserService` audita manifesto, persiste catálogo, promove chunks e conclui snapshot.

## Gargalos atuais

- Fan-out por chunk (`exists + copy + save`) no caminho de promoção.
- `CompletableFuture` por chunk em lote grande.
- I/O de storage misturado com transação em partes do ciclo de sessão.
- Rate limit em memória local (Caffeine), sem coordenação multi-instância.

## Legados para remover

- Endpoint potencialmente ocioso de registro de device (validar consumidores).
- Dependências/responsabilidades antigas no fluxo de chunk que não são mais usadas.
- Contratos internos ainda acoplados a caminhos stringly-typed.

## Melhorias objetivas

1. Promoção de chunk idempotente e race-safe (upsert + tratamento de conflito único).
2. Menos roundtrips em storage no caminho quente.
3. Separar I/O externo de transações longas.
4. Endurecer auth/rate-limit para ambiente distribuído.
