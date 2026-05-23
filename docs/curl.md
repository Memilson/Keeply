# Testes rápidos da API

## Registrar usuário

```bash
curl -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"name":"Aluno","email":"aluno@keeply.local","password":"123456"}'
```

## Login

```bash
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"aluno@keeply.local","password":"123456"}'
```

Copie o `accessToken`.

## Registrar device

```bash
curl -X POST http://localhost:8080/api/devices/register \
  -H "Authorization: Bearer TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"name":"notebook","hostname":"notebook","os":"Windows","agentVersion":"0.1.0"}'
```
