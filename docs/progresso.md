# Progresso e Status do Projeto Keeply

## Funcionalidades Implementadas

- [x] Agente funcional: realizando backup e restaurando.
  - *Observação atual:* O restore atual restaura todo o snapshot. (Ver em "Próximos Passos")
- [x] Envio do manifesto de chunks para o MinIO.
- [x] Comunicação Agente -> Backend via `BackendClient` para:
  - `POST /api/auth/login` (Login)
  - `POST /api/devices/register` (Registro do device)
  - `POST /api/snapshots/start` (Início do snapshot)
  - `POST /api/chunks/check` (Verificação de chunks)
  - `POST /api/chunks/upload` (Upload de chunks)
  - `POST /api/snapshots/{id}/complete` (Conclusão do snapshot com manifesto)
  - `POST /api/snapshots/{id}/fail` (Sinalização de falha do snapshot)

## Ambiente Local e Credenciais

| Serviço / Contexto | Chave | Valor |
| :--- | :--- | :--- |
| **MinIO** | Usuário | `keeply` |
| | Senha | `keeply123` |
| **PostgreSQL** | Database | `keeply` |
| | Usuário | `keeply` |
| | Senha | `keeply123` |
| **Aplicação (Backend/Agent)** | Email | `keeply@keeply.com` |
| | Senha | `keeply123` |

## Próximos Passos / Melhorias Identificadas

- [ ] **Melhoria do Restore:** Investigar e implementar restore parcial/granular em vez de exigir o restore de todo o snapshot (dúvida identificada na versão inicial do documento).
- [ ] *(Adicionar novos passos aqui conforme o andamento do projeto)*
