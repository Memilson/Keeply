# Arquitetura MinIO

## Escopo

MinIO é o data plane dos objetos de backup/restore. O backend emite credenciais temporárias e o agente transfere dados direto no bucket.

## Modelo atual

- Control plane:
  - `TransferCredentialBroker` abre/renova/fecha `transfer_sessions`.
  - `MinioStsCredentialIssuer` emite credenciais temporárias com policy por sessão.
- Data plane:
  - Agente usa `DirectTransferStorage` para `put/get` direto no MinIO.
  - Backend usa `MinioStorageService` para auditoria/promoção/limpeza.

## Chaves de objeto (resumo)

- Staging: `users/{userId}/transfer-sessions/{sessionId}/...`
- Chunks finais: `users/{userId}/chunks/{aa}/{bb}/{hash}.zst`
- Manifesto final: `users/{userId}/manifests/{snapshotId}.json.zst`

## Gargalos e riscos atuais

- Alto número de chamadas `exists`/`copy` por chunk no promote.
- Limpezas de prefixo potencialmente caras em cancelamento/expiração.
- Escopo de restore amplo (`users/{userId}/chunks/*`) para credencial temporária.
- Persistência de `minio_access_key` em sessão sem revogação real imediata.

## Legados para remover

- Segredos padrão fracos em exemplos/dev.
- Campos de sessão de transferência que não agregam segurança real.

## Melhorias objetivas

1. Reduzir roundtrips por chunk no pipeline de promoção.
2. Reforçar least-privilege e validade curta em credenciais temporárias.
3. Revisar revogação efetiva e trilha de auditoria da sessão.
4. Definir lifecycle policy para objetos órfãos de staging.
